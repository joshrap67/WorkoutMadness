package com.joshrap.liteweight.fragments;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.joshrap.liteweight.R;
import com.joshrap.liteweight.activities.WorkoutActivity;
import com.joshrap.liteweight.helpers.AndroidHelper;
import com.joshrap.liteweight.helpers.ImageHelper;
import com.joshrap.liteweight.helpers.InputHelper;
import com.joshrap.liteweight.imports.Globals;
import com.joshrap.liteweight.imports.Variables;
import com.joshrap.liteweight.injection.Injector;
import com.joshrap.liteweight.interfaces.FragmentWithDialog;
import com.joshrap.liteweight.models.Friend;
import com.joshrap.liteweight.models.FriendRequest;
import com.joshrap.liteweight.models.ResultStatus;
import com.joshrap.liteweight.models.User;
import com.joshrap.liteweight.network.repos.UserRepository;
import com.joshrap.liteweight.network.repos.WorkoutRepository;
import com.joshrap.liteweight.widgets.ErrorDialog;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import static android.os.Looper.getMainLooper;

public class FriendsListFragment extends Fragment implements FragmentWithDialog {
    private User user;
    private FloatingActionButton floatingActionButton;
    private TextView emptyView;
    private static final int FRIENDS_POSITION = 0;
    public static final int REQUESTS_POSITION = 1;
    private RecyclerView recyclerView;
    private AlertDialog alertDialog;
    private BottomSheetDialog bottomSheetDialog;
    private List<Friend> friends;
    private List<FriendRequest> friendRequests;
    private FriendsAdapter friendsAdapter;
    private FriendRequestsAdapter friendRequestsAdapter;
    private TabLayout tabLayout;
    private int currentIndex;
    @Inject
    ProgressDialog loadingDialog;
    @Inject
    UserRepository userRepository;
    @Inject
    WorkoutRepository workoutRepository;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends_list, container, false);
        ((WorkoutActivity) getActivity()).updateToolbarTitle(Variables.FRIENDS_LIST_TITLE);
        ((WorkoutActivity) getActivity()).toggleBackButton(true);
        Injector.getInjector(getContext()).inject(this);
        user = Globals.user;

        Bundle args = getArguments();
        if (args != null) {
            // don't need to consume the extras, as long as args are there we know we are starting on friend request page
            currentIndex = REQUESTS_POSITION;
        } else {
            currentIndex = FRIENDS_POSITION;
        }
        return view;
    }

    @Override
    public void onPause() {
        // sanity check to determine if user has any unseen requests after this fragment is paused
        if (tabLayout.getSelectedTabPosition() == REQUESTS_POSITION) {
            markAllRequestsSeen();
        }
        if (loadingDialog != null) {
            loadingDialog.dismiss();
        }
        super.onPause();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        /*
            Init all views and buttons when view is loaded onto screen
         */
        friends = new ArrayList<>();
        friendRequests = new ArrayList<>();

        friends.addAll(user.getFriends().values());
        friendRequests.addAll(user.getFriendRequests().values());
        sortFriendsList();
        sortFriendRequestList();

        friendRequestsAdapter = new FriendRequestsAdapter(friendRequests);
        friendsAdapter = new FriendsAdapter(friends);

        emptyView = view.findViewById(R.id.empty_view);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        recyclerView = view.findViewById(R.id.friends_recycler_view);
        recyclerView.setLayoutManager(llm);

        floatingActionButton = view.findViewById(R.id.floating_action_btn);
        floatingActionButton.setOnClickListener(v -> addFriendPopup());
        tabLayout = view.findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText("Friends"), FRIENDS_POSITION);
        boolean requestsUnseen = false;
        for (FriendRequest friendRequest : friendRequests) {
            if (!friendRequest.isSeen()) {
                requestsUnseen = true;
                break;
            }
        }
        tabLayout.addTab(tabLayout.newTab().setText(
                requestsUnseen ? "Friend Requests (!)" : "Friend Requests"), REQUESTS_POSITION);

        tabLayout.addOnTabSelectedListener(new TabLayout.BaseOnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == FRIENDS_POSITION) {
                    switchToFriendsList();
                } else if (tab.getPosition() == REQUESTS_POSITION) {
                    switchToRequestsList();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                if (tab.getPosition() == REQUESTS_POSITION) {
                    markAllRequestsSeen();
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        if (currentIndex == REQUESTS_POSITION) {
            tabLayout.getTabAt(REQUESTS_POSITION).select();
        } else {
            switchToFriendsList();
        }
        super.onViewCreated(view, savedInstanceState);
    }

    private void sortFriendsList() {
        Collections.sort(friends, (friend, t1) -> friend.getUsername().compareTo(t1.getUsername()));
    }

    private void sortFriendRequestList() {
        Collections.sort(friendRequests, (friendRequest, t1) -> {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            int retVal = 0;
            try {
                Date date1 = sdf.parse(friendRequest.getRequestTimeStamp());
                Date date2 = sdf.parse(t1.getRequestTimeStamp());
                retVal = date1 != null ? date1.compareTo(date2) : 0;
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return retVal;
        });
    }

    @Override
    public void hideAllDialogs() {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
        if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) {
            bottomSheetDialog.dismiss();
        }
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void switchToFriendsList() {
        floatingActionButton.show();
        checkEmptyList(FRIENDS_POSITION);
        friendsAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            // since google is stupid af and doesn't have a simple setEmptyView for recyclerView...
            @Override
            public void onChanged() {
                super.onChanged();
                checkEmptyList(FRIENDS_POSITION);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                checkEmptyList(FRIENDS_POSITION);
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);
                checkEmptyList(FRIENDS_POSITION);
            }
        });
        recyclerView.setAdapter(friendsAdapter);
    }

    private void switchToRequestsList() {
        tabLayout.getTabAt(REQUESTS_POSITION).setText("Friend Requests"); // when user clicks on this tab, all requests are set to "seen"
        floatingActionButton.hide();
        checkEmptyList(REQUESTS_POSITION);
        friendsAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            // since google is stupid af and doesn't have a simple setEmptyView for recyclerView...
            @Override
            public void onChanged() {
                super.onChanged();
                checkEmptyList(REQUESTS_POSITION);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                checkEmptyList(REQUESTS_POSITION);
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);
                checkEmptyList(REQUESTS_POSITION);
            }
        });
        recyclerView.setAdapter(friendRequestsAdapter);
        deleteNotifications();
    }

    private void checkEmptyList(int position) {
         /*
            Used to check if the user has any friends. If not, show a textview alerting user
         */
        if (position == FRIENDS_POSITION) {
            emptyView.setVisibility(friends.isEmpty() ? View.VISIBLE : View.GONE);
            emptyView.setText(getString(R.string.empty_friend_list_msg));
        } else if (position == REQUESTS_POSITION) {
            emptyView.setVisibility(friendRequests.isEmpty() ? View.VISIBLE : View.GONE);
            emptyView.setText(getString(R.string.empty_friends_request_msg));
        }
    }

    private void deleteNotifications() {
        for (FriendRequest friendRequest : friendRequests) {
            if (!friendRequest.isSeen()) {
                // get rid of any push notification that might be there
                NotificationManager notificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.cancel(friendRequest.getUsername().hashCode());
                }
            }
        }
    }

    private void markAllRequestsSeen() {
        int unseenCount = 0;
        for (FriendRequest friendRequest : friendRequests) {
            if (!friendRequest.isSeen()) {
                unseenCount++;
                friendRequest.setSeen(true);
            }
        }
        if (unseenCount > 0) {
            // prevents useless api calls to update unseen friend requests
            if (getActivity() != null) {
                deleteNotifications();
                ((WorkoutActivity) getActivity()).updateAccountNotificationIndicator();
            }
            // marking all requests seen is not critical at all, so if it fails no need to alarm user
            Executor executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> userRepository.setAllRequestsSeen());
        }
    }

    private void addFriendPopup() {
        final View popupView = getLayoutInflater().inflate(R.layout.popup_add_friend, null);
        final TextInputLayout friendNameLayout = popupView.findViewById(R.id.friend_name_input_layout);
        final EditText friendInput = popupView.findViewById(R.id.friend_name_input);
        friendInput.addTextChangedListener(AndroidHelper.hideErrorTextWatcher(friendNameLayout));
        friendInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Variables.MAX_USERNAME_LENGTH)});
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Add Friend")
                .setView(popupView)
                .setPositiveButton("Send Request", null)
                .setNegativeButton("Close", null)
                .create();
        alertDialog.setOnShowListener(dialogInterface -> {
            Button saveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(view -> {
                String friendUsername = friendInput.getText().toString().trim();
                List<String> existingUsernames = new ArrayList<>();
                for (Friend friend : friends) {
                    existingUsernames.add(friend.getUsername());
                }
                for (FriendRequest friendRequest : friendRequests) {
                    existingUsernames.add(friendRequest.getUsername());
                }
                String errorMsg = InputHelper.validNewFriend(user.getUsername(), friendUsername, existingUsernames);
                if (errorMsg != null) {
                    friendNameLayout.setError(errorMsg);
                } else {
                    // no problems so go ahead and save
                    alertDialog.dismiss();
                    sendFriendRequest(friendUsername);
                }
            });
        });
        alertDialog.show();
    }

    private void blockUserPopup(String username) {
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Block User")
                .setMessage(String.format("Are you sure you wish to block \"%s\"? They will no longer be able to add you as a friend or send you any workouts.", username))
                .setPositiveButton("Yes", (dialog, which) -> blockUser(username))
                .setNegativeButton("No", null)
                .create();
        alertDialog.show();
    }

    private void showLoadingDialog(String message) {
        loadingDialog.setMessage(message);
        loadingDialog.show();
    }

    private void sendFriendRequest(String username) {
        showLoadingDialog("Sending request...");
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<Friend> resultStatus = this.userRepository.sendFriendRequest(username);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                loadingDialog.dismiss();
                if (resultStatus.isSuccess()) {
                    user.getFriends().put(resultStatus.getData().getUsername(), resultStatus.getData());
                    friends.add(user.getFriends().get(username));
                    sortFriendsList();
                    friendsAdapter.notifyDataSetChanged();
                } else {
                    ErrorDialog.showErrorDialog("Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    private void acceptFriendRequest(String username) {
        // we assume it always succeeds
        FriendRequest friendRequest = user.getFriendRequests().get(username);
        user.getFriendRequests().remove(username);
        friendRequests.remove(friendRequest);
        friendRequestsAdapter.notifyDataSetChanged();
        checkEmptyList(REQUESTS_POSITION);

        Friend friend = new Friend(friendRequest.getIcon(), true, username);
        Globals.user.getFriends().put(username, friend);
        friends.add(friend);
        sortFriendsList();

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<String> resultStatus = this.userRepository.acceptFriendRequest(username);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                // not critical to show any type of loading dialog/handle errors for this action.
                if (!resultStatus.isSuccess()) {
                    ErrorDialog.showErrorDialog("Error", resultStatus.getErrorMessage(), getContext());
                    // todo remove user from list?
                }
            });
        });
    }

    private void declineFriendRequest(String username) {
        // we assume it always succeeds
        FriendRequest friendRequest = user.getFriendRequests().get(username);
        user.getFriendRequests().remove(username);
        friendRequests.remove(friendRequest);
        friendRequestsAdapter.notifyDataSetChanged();
        checkEmptyList(REQUESTS_POSITION);

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<String> resultStatus = this.userRepository.declineFriendRequest(username);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                // not critical to show any type of loading dialog/handle errors for this action.
                if (!resultStatus.isSuccess()) {
                    ErrorDialog.showErrorDialog("Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    private void blockUser(String username) {
        showLoadingDialog("Blocking user...");

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<String> resultStatus = this.userRepository.blockUser(username);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                loadingDialog.dismiss();
                if (resultStatus.isSuccess()) {
                    user.getBlocked().put(username, resultStatus.getData());
                    // this maybe shouldn't be the frontend's responsibility, but i would have to change the backend a bit otherwise so oh well
                    user.getFriendRequests().remove(username);
                    user.getFriends().remove(username);
                    if (tabLayout.getSelectedTabPosition() == FRIENDS_POSITION) {
                        Friend friendToRemove = null;
                        for (Friend friend : friends) {
                            if (friend.getUsername().equals(username)) {
                                friendToRemove = friend;
                            }
                        }
                        friends.remove(friendToRemove);
                        friendsAdapter.notifyDataSetChanged();
                        checkEmptyList(FRIENDS_POSITION);
                    } else {
                        FriendRequest requestToRemove = null;
                        for (FriendRequest friendRequest : friendRequests) {
                            if (friendRequest.getUsername().equals(username)) {
                                requestToRemove = friendRequest;
                            }
                        }
                        friendRequests.remove(requestToRemove);
                        friendRequestsAdapter.notifyDataSetChanged();
                        checkEmptyList(REQUESTS_POSITION);
                    }
                } else {
                    ErrorDialog.showErrorDialog("Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    private void removeFriend(String username) {
        // we assume it always succeeds
        Friend friend = user.getFriends().get(username);
        Globals.user.getFriends().remove(username);
        friends.remove(friend);
        friendsAdapter.notifyDataSetChanged();
        checkEmptyList(FRIENDS_POSITION);

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<String> resultStatus = this.userRepository.removeFriend(username);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                // not critical to show any type of loading dialog/handle errors for this action.
                if (!resultStatus.isSuccess()) {
                    ErrorDialog.showErrorDialog("Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    private void promptSend(String friendUsername) {
        /*
            Prompt user to send workout to a friend or any other user
         */
        final View popupView = getLayoutInflater().inflate(R.layout.popup_send_workout_pick_workout, null);
        final RadioGroup workoutsRadioGroup = popupView.findViewById(R.id.workouts_radio_group);
        List<String> workoutNames = new ArrayList<>();
        Map<String, String> workoutNameToId = new HashMap<>();
        for (String workoutId : user.getUserWorkouts().keySet()) {
            workoutNameToId.put(user.getUserWorkouts().get(workoutId).getWorkoutName(), workoutId);
            workoutNames.add(user.getUserWorkouts().get(workoutId).getWorkoutName());
        }
        int id = 0;
        for (String workoutName : workoutNames) {
            RadioButton radioButton = new RadioButton(getContext());
            radioButton.setId(id);
            radioButton.setTextColor(getResources().getColor(R.color.defaultTextColor)); // hate this but don't know another way
            radioButton.setText(workoutName);
            radioButton.setTextSize(16);
            workoutsRadioGroup.addView(radioButton);
            id++;
        }
        if (workoutNames.isEmpty()) {
            // user has no workouts to send
            TextView workoutTV = popupView.findViewById(R.id.workouts_text_view);
            workoutTV.setText("You have no workouts to send.");
            workoutsRadioGroup.setVisibility(View.GONE);
        }
        Collections.sort(workoutNames, String::compareToIgnoreCase);

        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle(String.format("Send a workout to \"%s\"", friendUsername))
                .setView(popupView)
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", null)
                .create();
        alertDialog.setOnShowListener(dialogInterface -> {
            final Button sendButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (workoutNames.isEmpty()) {
                sendButton.setVisibility(View.GONE);
            }
            sendButton.setOnClickListener(view -> {
                int selectedId = workoutsRadioGroup.getCheckedRadioButtonId();
                if (selectedId == -1) {
                    // no workout is selected
                    Toast.makeText(getContext(), "Please select a workout to send.", Toast.LENGTH_LONG).show();
                } else {
                    RadioButton radioButtonSelected = popupView.findViewById(selectedId);
                    sendWorkout(friendUsername, workoutNameToId.get(radioButtonSelected.getText().toString()));
                    alertDialog.dismiss();
                }
            });
        });
        alertDialog.show();
    }

    private void sendWorkout(final String recipientUsername, String workoutId) {
        showLoadingDialog("Sending...");
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<String> resultStatus = this.workoutRepository.sendWorkout(recipientUsername, workoutId);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                loadingDialog.dismiss();
                if (resultStatus.isSuccess()) {
                    Toast.makeText(getContext(), "Workout successfully sent.", Toast.LENGTH_LONG).show();
                    user.setWorkoutsSent(user.getWorkoutsSent() + 1);

                } else {
                    ErrorDialog.showErrorDialog("Copy Workout Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    private void cancelFriendRequest(String username) {
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<String> resultStatus = this.userRepository.cancelFriendRequest(username);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                // not critical to show any type of loading dialog/handle errors for this action
                if (!resultStatus.isSuccess()) {
                    ErrorDialog.showErrorDialog("Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    public void removeFriendRequestFromList(String username) {
        FriendRequest friendRequestToRemove = null;
        for (FriendRequest friendRequest : friendRequests) {
            if (friendRequest.getUsername().equals(username)) {
                friendRequestToRemove = friendRequest;
                break;
            }
        }
        if (friendRequestToRemove != null) {
            friendRequests.remove(friendRequestToRemove);
            friendRequestsAdapter.notifyDataSetChanged();
            checkEmptyList(tabLayout.getSelectedTabPosition());
        }
    }

    public void removeFriendFromList(String username) {
        Friend friendToRemove = null;
        for (Friend friend : friends) {
            if (friend.getUsername().equals(username)) {
                friendToRemove = friend;
                break;
            }
        }
        if (friendToRemove != null) {
            friends.remove(friendToRemove);
            friendsAdapter.notifyDataSetChanged();
            checkEmptyList(tabLayout.getSelectedTabPosition());
        }
    }

    public void addFriendRequestToList(FriendRequest friendRequest) {
        if (friendRequest != null) {
            friendRequests.add(0, friendRequest);
            sortFriendRequestList();
            Toast.makeText(getContext(), friendRequest.getUsername() + " sent you a friend request.", Toast.LENGTH_LONG).show();
            friendRequestsAdapter.notifyDataSetChanged();
            checkEmptyList(tabLayout.getSelectedTabPosition());
        }
    }

    public void updateFriendsList() {
        sortFriendsList();
        friendsAdapter.notifyDataSetChanged();
        checkEmptyList(tabLayout.getSelectedTabPosition());
    }

    private void showBlownUpProfilePic(Friend friend) {
        View popupView = getLayoutInflater().inflate(R.layout.popup_blown_up_profile_picture, null);
        final ImageView profilePicture = popupView.findViewById(R.id.profile_picture);
        Picasso.get()
                .load(ImageHelper.getIconUrl(friend.getIcon()))
                .error(R.drawable.app_icon_no_background)
                .networkPolicy(NetworkPolicy.NO_CACHE) // on first loading in app, always fetch online
                .into(profilePicture);

        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle(friend.getUsername())
                .setView(popupView)
                .setPositiveButton("Done", null)
                .create();
        alertDialog.show();
    }

    private void showBlownUpProfilePic(FriendRequest friend) {
        // todo use same method for both friend and friend request
        View popupView = getLayoutInflater().inflate(R.layout.popup_blown_up_profile_picture, null);
        final ImageView profilePicture = popupView.findViewById(R.id.profile_picture);
        Picasso.get()
                .load(ImageHelper.getIconUrl(friend.getIcon()))
                .error(R.drawable.app_icon_no_background)
                .networkPolicy(NetworkPolicy.NO_CACHE) // on first loading in app, always fetch online
                .into(profilePicture);

        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle(friend.getUsername())
                .setView(popupView)
                .setPositiveButton("Done", null)
                .create();
        alertDialog.show();
    }

    // region Adapters

    private class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView usernameTV;
            TextView pendingTV;
            ImageView profilePicture;
            ConstraintLayout rootLayout;

            ViewHolder(View itemView) {
                super(itemView);
                pendingTV = itemView.findViewById(R.id.pending_request_tv);
                rootLayout = itemView.findViewById(R.id.root_layout);
                usernameTV = itemView.findViewById(R.id.username_tv);
                profilePicture = itemView.findViewById(R.id.profile_picture);
            }
        }

        private List<Friend> friends;

        FriendsAdapter(List<Friend> friends) {
            this.friends = friends;
        }

        @Override
        public FriendsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            View friendView = inflater.inflate(R.layout.row_friend, parent, false);
            return new FriendsAdapter.ViewHolder(friendView);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        // Involves populating data into the item through holder
        @Override
        public void onBindViewHolder(FriendsAdapter.ViewHolder holder, int position) {
            // Get the data model based on position
            final Friend friend = friends.get(position);

            final ConstraintLayout rootLayout = holder.rootLayout;
            rootLayout.setOnClickListener(v -> {
                bottomSheetDialog = new BottomSheetDialog(getActivity(), R.style.BottomSheetDialogTheme);
                View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_friends_list, null);
                final TextView sendWorkout = sheetView.findViewById(R.id.send_friend_workout_tv);
                final TextView removeFriend = sheetView.findViewById(R.id.remove_friend_tv);

                final TextView blockFriend = sheetView.findViewById(R.id.block_friend_tv);
                final TextView cancelRequest = sheetView.findViewById(R.id.cancel_friend_request_tv);
                sendWorkout.setVisibility((friend.isConfirmed() ? View.VISIBLE : View.GONE));
                sendWorkout.setOnClickListener(view -> {
                    bottomSheetDialog.dismiss();
                    promptSend(friend.getUsername());
                });
                removeFriend.setVisibility((friend.isConfirmed() ? View.VISIBLE : View.GONE));
                removeFriend.setOnClickListener(view -> {
                    bottomSheetDialog.dismiss();
                    removeFriend(friend.getUsername());
                });
                blockFriend.setVisibility((friend.isConfirmed() ? View.VISIBLE : View.GONE));
                blockFriend.setOnClickListener(view -> {
                    bottomSheetDialog.dismiss();
                    blockUserPopup(friend.getUsername());
                });
                cancelRequest.setVisibility((friend.isConfirmed() ? View.GONE : View.VISIBLE));
                cancelRequest.setOnClickListener(view -> {
                    cancelFriendRequest(friend.getUsername());
                    bottomSheetDialog.dismiss();
                    user.getFriends().remove(friend.getUsername());
                    friends.remove(friend);
                    notifyDataSetChanged();
                });

                final RelativeLayout relativeLayout = sheetView.findViewById(R.id.username_pic_container);
                relativeLayout.setOnClickListener(v1 -> showBlownUpProfilePic(friend));
                final TextView usernameTV = sheetView.findViewById(R.id.username_tv);
                final ImageView profilePicture = sheetView.findViewById(R.id.profile_picture);
                usernameTV.setText(friend.getUsername());

                Picasso.get()
                        .load(ImageHelper.getIconUrl(friend.getIcon()))
                        .error(R.drawable.app_icon_no_background)
                        .networkPolicy(NetworkPolicy.NO_CACHE) // on first loading in app, always fetch online
                        .into(profilePicture, new com.squareup.picasso.Callback() {
                            @Override
                            public void onSuccess() {
                                Bitmap imageBitmap = ((BitmapDrawable) profilePicture.getDrawable()).getBitmap();
                                RoundedBitmapDrawable imageDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageBitmap);
                                imageDrawable.setCircular(true);
                                imageDrawable.setCornerRadius(Math.max(imageBitmap.getWidth(), imageBitmap.getHeight()) / 2.0f);
                                profilePicture.setImageDrawable(imageDrawable);
                            }

                            @Override
                            public void onError(Exception e) {
                            }
                        });

                bottomSheetDialog.setContentView(sheetView);
                bottomSheetDialog.show();
            });
            final TextView exerciseTV = holder.usernameTV;
            final TextView pendingTV = holder.pendingTV;
            pendingTV.setVisibility(friend.isConfirmed() ? View.GONE : View.VISIBLE);
            final ImageView profilePicture = holder.profilePicture;
            exerciseTV.setText(friend.getUsername());
            Picasso.get()
                    .load(ImageHelper.getIconUrl(friend.getIcon()))
                    .error(R.drawable.app_icon_no_background)
                    .networkPolicy(NetworkPolicy.NO_CACHE) // on first loading in app, always fetch online
                    .into(profilePicture, new com.squareup.picasso.Callback() {
                        @Override
                        public void onSuccess() {
                            if (FriendsListFragment.this.isResumed()) {
                                Bitmap imageBitmap = ((BitmapDrawable) profilePicture.getDrawable()).getBitmap();
                                RoundedBitmapDrawable imageDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageBitmap);
                                imageDrawable.setCircular(true);
                                imageDrawable.setCornerRadius(Math.max(imageBitmap.getWidth(), imageBitmap.getHeight()) / 2.0f);
                                profilePicture.setImageDrawable(imageDrawable);
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                        }
                    });
        }

        // Returns the total count of items in the list
        @Override
        public int getItemCount() {
            return friends.size();
        }
    }

    private class FriendRequestsAdapter extends RecyclerView.Adapter<FriendRequestsAdapter.ViewHolder> {
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView usernameTV;
            TextView unseenTV;
            ImageView profilePicture;
            Button acceptRequestButton;
            Button blockButton;
            Button declineRequestButton;

            ViewHolder(View itemView) {
                super(itemView);
                usernameTV = itemView.findViewById(R.id.username_tv);
                profilePicture = itemView.findViewById(R.id.profile_picture);
                acceptRequestButton = itemView.findViewById(R.id.accept_request_btn);
                declineRequestButton = itemView.findViewById(R.id.decline_request_btn);
                blockButton = itemView.findViewById(R.id.block_btn);
                unseenTV = itemView.findViewById(R.id.unseen_tv);
            }
        }

        private List<FriendRequest> friendRequests;

        FriendRequestsAdapter(List<FriendRequest> friends) {
            this.friendRequests = friends;
        }

        @Override
        public FriendRequestsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            View friendView = inflater.inflate(R.layout.row_friend_request, parent, false);
            return new FriendRequestsAdapter.ViewHolder(friendView);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        // Involves populating data into the item through holder
        @Override
        public void onBindViewHolder(FriendRequestsAdapter.ViewHolder holder, int position) {
            // Get the data model based on position
            final FriendRequest friendRequest = friendRequests.get(position);
            final TextView exerciseTV = holder.usernameTV;
            final ImageView profilePicture = holder.profilePicture;
            final TextView unseenTV = holder.unseenTV;
            final Button acceptRequestButton = holder.acceptRequestButton;
            final Button declineRequestButton = holder.declineRequestButton;
            final Button blockButton = holder.blockButton;
            acceptRequestButton.setOnClickListener(view -> acceptFriendRequest(friendRequest.getUsername()));
            declineRequestButton.setOnClickListener(view -> declineFriendRequest(friendRequest.getUsername()));
            blockButton.setOnClickListener(view -> blockUserPopup(friendRequest.getUsername()));
            unseenTV.setVisibility(friendRequest.isSeen() ? View.GONE : View.VISIBLE);
            profilePicture.setOnClickListener(v -> showBlownUpProfilePic(friendRequest));
            exerciseTV.setText(friendRequest.getUsername());
            Picasso.get()
                    .load(ImageHelper.getIconUrl(friendRequest.getIcon()))
                    .error(R.drawable.app_icon_no_background)
                    .networkPolicy(NetworkPolicy.NO_CACHE) // on first loading in app, always fetch online
                    .into(profilePicture, new com.squareup.picasso.Callback() {
                        @Override
                        public void onSuccess() {
                            if (FriendsListFragment.this.isResumed()) {
                                Bitmap imageBitmap = ((BitmapDrawable) profilePicture.getDrawable()).getBitmap();
                                RoundedBitmapDrawable imageDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageBitmap);
                                imageDrawable.setCircular(true);
                                imageDrawable.setCornerRadius(Math.max(imageBitmap.getWidth(), imageBitmap.getHeight()) / 2.0f);
                                profilePicture.setImageDrawable(imageDrawable);
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                        }
                    });
        }

        // Returns the total count of items in the list
        @Override
        public int getItemCount() {
            return friendRequests.size();
        }
    }
    //endregion
}
