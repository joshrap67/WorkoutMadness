package com.joshrap.liteweight.fragments;

import android.app.AlertDialog;

import android.app.ProgressDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;
import com.joshrap.liteweight.*;
import com.joshrap.liteweight.activities.WorkoutActivity;
import com.joshrap.liteweight.adapters.WorkoutsAdapter;
import com.joshrap.liteweight.utils.AndroidUtils;
import com.joshrap.liteweight.utils.ValidatorUtils;
import com.joshrap.liteweight.utils.StatisticsUtils;
import com.joshrap.liteweight.imports.Variables;
import com.joshrap.liteweight.injection.Injector;
import com.joshrap.liteweight.interfaces.FragmentWithDialog;
import com.joshrap.liteweight.models.ResultStatus;
import com.joshrap.liteweight.models.User;
import com.joshrap.liteweight.models.UserWithWorkout;
import com.joshrap.liteweight.models.Workout;
import com.joshrap.liteweight.models.WorkoutMeta;
import com.joshrap.liteweight.network.repos.WorkoutRepository;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import static android.os.Looper.getMainLooper;

public class MyWorkoutsFragment extends Fragment implements FragmentWithDialog {
    private TextView selectedWorkoutTV, statisticsTV;
    private ListView workoutListView;
    private AlertDialog alertDialog;
    private User user;
    private Workout currentWorkout;
    private UserWithWorkout userWithWorkout;
    private List<WorkoutMeta> workoutList;
    private WorkoutsAdapter workoutsAdapter;
    @Inject
    ProgressDialog loadingDialog;
    @Inject
    WorkoutRepository workoutRepository;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Injector.getInjector(getContext()).inject(this);

        ((WorkoutActivity) getActivity()).updateToolbarTitle(Variables.MY_WORKOUT_TITLE);
        ((WorkoutActivity) getActivity()).toggleBackButton(false);

        userWithWorkout = ((WorkoutActivity) getActivity()).getUserWithWorkout();
        currentWorkout = userWithWorkout.getWorkout();
        user = userWithWorkout.getUser();

        View view;
        if (!userWithWorkout.isWorkoutPresent()) {
            view = inflater.inflate(R.layout.no_workouts_found_layout, container, false);
        } else {
            view = inflater.inflate(R.layout.fragment_my_workouts, container, false);
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!userWithWorkout.isWorkoutPresent()) {
            FloatingActionButton createWorkoutBtn = view.findViewById(R.id.create_workout_btn);
            createWorkoutBtn.setOnClickListener(v -> ((WorkoutActivity) getActivity()).goToNewWorkout());
            return;
        }
        workoutList = new ArrayList<>(user.getWorkoutMetas().values());
        initViews(view);
    }

    @Override
    public void onPause() {
        hideAllDialogs();
        super.onPause();
    }

    @Override
    public void hideAllDialogs() {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    /**
     * Once at least one workout is found, change layouts and initialize all views.
     *
     * @param view root view with all necessary widgets
     */
    private void initViews(View view) {
        ImageButton workoutOptionsButton = view.findViewById(R.id.workout_options_btn);
        PopupMenu dropDownMenu = new PopupMenu(getContext(), workoutOptionsButton);
        Menu menu = dropDownMenu.getMenu();
        final int editIndex = 0;
        final int sendIndex = 1;
        final int copyIndex = 2;
        final int renameIndex = 3;
        final int resetIndex = 4;
        final int deleteIndex = 5;
        menu.add(0, editIndex, 0, "Edit Workout");
        menu.add(0, sendIndex, 0, "Share Workout");
        menu.add(0, copyIndex, 0, "Copy Workout");
        menu.add(0, renameIndex, 0, "Rename Workout");
        menu.add(0, resetIndex, 0, "Reset Statistics");
        menu.add(0, deleteIndex, 0, "Delete Workout");

        dropDownMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case editIndex:
                    dropDownMenu.dismiss();
                    ((WorkoutActivity) getActivity()).goToEditWorkout();
                    return true;
                case renameIndex:
                    promptRename();
                    return true;
                case resetIndex:
                    promptResetStatistics();
                    return true;
                case deleteIndex:
                    promptDelete();
                    return true;
                case sendIndex:
                    if (user.getPremiumToken() != null ||
                            user.getWorkoutsSent() < Variables.MAX_FREE_WORKOUTS_SENT) {
                        promptShare();
                    } else {
                        AndroidUtils.showErrorDialog("Too many workouts sent", "You have reached the maximum number of workouts that you can send.", getContext());
                    }
                    return true;
                case copyIndex:
                    if (user.getPremiumToken() == null &&
                            workoutList.size() >= Variables.MAX_FREE_WORKOUTS) {
                        AndroidUtils.showErrorDialog("Too many workouts", "Copying this workout would put you over the maximum amount of workouts you can own. Delete some of your other ones if you wish to copy this workout.", getContext());
                    } else if (user.getPremiumToken() != null
                            && workoutList.size() >= Variables.MAX_WORKOUTS) {
                        AndroidUtils.showErrorDialog("Too many workouts", "Copying this workout would put you over the maximum amount of workouts you can own. Delete some of your other ones if you wish to copy this workout.", getContext());
                    } else {
                        promptCopy();
                    }
                    return true;
            }
            return false;
        });
        workoutOptionsButton.setOnClickListener(v -> dropDownMenu.show());

        workoutListView = view.findViewById(R.id.workout_list);
        selectedWorkoutTV = view.findViewById(R.id.selected_workout_text_view);
        statisticsTV = view.findViewById(R.id.stat_text_view);
        selectedWorkoutTV.setText(currentWorkout.getWorkoutName());
        updateStatisticsTV();

        FloatingActionButton createWorkoutBtn = view.findViewById(R.id.new_workout_btn);
        createWorkoutBtn.setOnClickListener(v -> {
            if (user.getPremiumToken() == null
                    && workoutList.size() >= Variables.MAX_FREE_WORKOUTS) {
                AndroidUtils.showErrorDialog("Too many workouts", "You have reached the maximum amount of workouts allowed. Delete some of your other ones if you wish to create a new one.", getContext());
            } else if (user.getPremiumToken() != null
                    && workoutList.size() >= Variables.MAX_WORKOUTS) {
                AndroidUtils.showErrorDialog("Too many workouts", "You have reached the maximum amount of workouts allowed. Delete some of your other ones if you wish to create a new one.", getContext());
            } else {
                // no errors so let user create new workout
                ((WorkoutActivity) getActivity()).goToNewWorkout();
            }
        });

        // initializes the main list view
        sortWorkouts();
        workoutsAdapter = new WorkoutsAdapter(getContext(), workoutList);
        workoutListView.setAdapter(workoutsAdapter);
        workoutListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        workoutListView.setOnItemClickListener((parent, _view, position, id) ->
                switchWorkout(workoutList.get(position)));
        workoutListView.setItemChecked(0, true); // programmatically select current workout in list
    }

    /**
     * Updates all UI with the newly changed current workout.
     */
    private void updateUI() {
        workoutList.clear();
        workoutList.addAll(user.getWorkoutMetas().values());
        selectedWorkoutTV.setText(currentWorkout.getWorkoutName());
        sortWorkouts();
        workoutsAdapter.notifyDataSetChanged();
        updateStatisticsTV();
    }

    /**
     * Sorts workouts by date last accessed and ensures currently selected workout is at the top of the list.
     */
    private void sortWorkouts() {
        WorkoutMeta currentWorkoutMeta = user.getWorkoutMetas().get(currentWorkout.getWorkoutId());
        workoutList.remove(currentWorkoutMeta);
        Collections.sort(workoutList, (r1, r2) -> {
            DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
            dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            int retVal = 0;
            try {
                Date date1 = dateFormatter.parse(r1.getDateLast());
                Date date2 = dateFormatter.parse(r2.getDateLast());
                retVal = date1 != null ? date2.compareTo(date1) : 0;
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return retVal;
        });
        workoutList.add(0, currentWorkoutMeta); // selected always on top
        workoutListView.setItemChecked(0, true); // programmatically select current workout in list
    }

    /**
     * Fetches and displays statistics for the currently selected workout.
     */
    private void updateStatisticsTV() {
        int timesCompleted = user.getWorkoutMetas().get(currentWorkout.getWorkoutId()).getTimesCompleted();
        double average = user.getWorkoutMetas().get(currentWorkout.getWorkoutId()).getAverageExercisesCompleted();
        String formattedPercentage = StatisticsUtils.getFormattedAverageCompleted(average);
        String msg = "Times Completed: " + timesCompleted + "\n" +
                "Average Percentage of Exercises Completed: " + formattedPercentage + "\n" +
                "Total Number of Days in Workout: " + currentWorkout.getRoutine().getTotalNumberOfDays() + "\n" +
                "Most Worked Focus: " + currentWorkout.getMostFrequentFocus().replaceAll(",", ", ");
        statisticsTV.setText(msg);
    }

    /**
     * Prompt the user if they actually want to reset the selected workout's statistics.
     */
    private void promptResetStatistics() {
        String message = "Are you sure you wish to reset the statistics for \"" +
                currentWorkout.getWorkoutName() + "\"?\n\n" +
                "Doing so will reset the times completed and the percentage of exercises completed.";
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Reset Statistics")
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> resetWorkoutStatistics(currentWorkout.getWorkoutId()))
                .setNegativeButton("No", null)
                .create();
        alertDialog.show();
    }

    private void resetWorkoutStatistics(String workoutId) {
        AndroidUtils.showLoadingDialog(loadingDialog, "Resetting...");
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<WorkoutMeta> resultStatus = this.workoutRepository.resetWorkoutStatistics(workoutId);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                loadingDialog.dismiss();
                if (resultStatus.isSuccess()) {
                    user.getWorkoutMetas().put(currentWorkout.getWorkoutId(), resultStatus.getData());

                    updateUI();
                } else {
                    AndroidUtils.showErrorDialog("Reset Workout Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    /**
     * Prompt the user if they want to rename the current workout.
     */
    private void promptRename() {
        View popupView = getLayoutInflater().inflate(R.layout.popup_rename_workout, null);
        EditText renameInput = popupView.findViewById(R.id.rename_workout_name_input);
        TextInputLayout workoutNameInputLayout = popupView.findViewById(R.id.rename_workout_name_input_layout);
        renameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Variables.MAX_WORKOUT_NAME)});
        renameInput.addTextChangedListener(AndroidUtils.hideErrorTextWatcher(workoutNameInputLayout));
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Rename \"" + currentWorkout.getWorkoutName() + "\"")
                .setView(popupView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        alertDialog.setOnShowListener(dialogInterface -> {
            Button saveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(view -> {
                String newName = renameInput.getText().toString().trim();
                List<String> workoutNames = new ArrayList<>();
                for (WorkoutMeta workoutMeta : workoutList) {
                    workoutNames.add(workoutMeta.getWorkoutName());
                }
                String errorMsg = ValidatorUtils.validWorkoutName(newName, workoutNames);
                if (errorMsg != null) {
                    workoutNameInputLayout.setError(errorMsg);
                } else {
                    alertDialog.dismiss();
                    renameWorkout(currentWorkout.getWorkoutId(), newName);
                }
            });
        });
        alertDialog.show();
    }

    private void renameWorkout(String workoutId, String newWorkoutName) {
        AndroidUtils.showLoadingDialog(loadingDialog, "Renaming...");
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<User> resultStatus = this.workoutRepository.renameWorkout(workoutId, newWorkoutName);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                loadingDialog.dismiss();
                if (resultStatus.isSuccess()) {
                    user.updateOwnedExercises(resultStatus.getData().getOwnedExercises());
                    user.getWorkoutMetas().get(currentWorkout.getWorkoutId()).setWorkoutName(newWorkoutName);
                    currentWorkout.setWorkoutName(newWorkoutName);

                    updateUI();
                } else {
                    AndroidUtils.showErrorDialog("Rename Workout Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    private void promptCopy() {
        View popupView = getLayoutInflater().inflate(R.layout.popup_copy_workout, null);
        EditText workoutNameInput = popupView.findViewById(R.id.workout_name_input);
        TextInputLayout workoutNameInputLayout = popupView.findViewById(R.id.workout_name_input_layout);
        workoutNameInput.addTextChangedListener(AndroidUtils.hideErrorTextWatcher(workoutNameInputLayout));
        workoutNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Variables.MAX_WORKOUT_NAME)});
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle(String.format("Copy \"%s\" as new workout", currentWorkout.getWorkoutName()))
                .setView(popupView)
                .setPositiveButton("Copy", null)
                .setNegativeButton("Cancel", null)
                .create();
        alertDialog.setOnShowListener(dialogInterface -> {
            Button saveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(view -> {
                String workoutName = workoutNameInput.getText().toString().trim();
                List<String> workoutNames = new ArrayList<>();
                for (String workoutId : user.getWorkoutMetas().keySet()) {
                    workoutNames.add(user.getWorkoutMetas().get(workoutId).getWorkoutName());
                }
                String errorMsg = ValidatorUtils.validWorkoutName(workoutName, workoutNames);
                if (errorMsg != null) {
                    workoutNameInputLayout.setError(errorMsg);
                } else {
                    // no problems so go ahead and save
                    alertDialog.dismiss();
                    copyWorkout(workoutName);
                }
            });
        });
        alertDialog.show();
    }

    private void copyWorkout(String workoutName) {
        AndroidUtils.showLoadingDialog(loadingDialog, "Copying...");
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<UserWithWorkout> resultStatus = this.workoutRepository.copyWorkout(currentWorkout, workoutName);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                loadingDialog.dismiss();
                if (resultStatus.isSuccess()) {
                    userWithWorkout.setWorkout(resultStatus.getData().getWorkout());
                    currentWorkout = userWithWorkout.getWorkout();

                    user.setCurrentWorkout(resultStatus.getData().getUser().getCurrentWorkout());
                    user.getWorkoutMetas().put(currentWorkout.getWorkoutId(),
                            resultStatus.getData().getUser().getWorkoutMetas().get(currentWorkout.getWorkoutId()));

                    updateUI();
                } else {
                    AndroidUtils.showErrorDialog("Copy Workout Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    /**
     * Prompt user to send workout to a friend or any other user
     */
    private void promptShare() {
        View popupView = getLayoutInflater().inflate(R.layout.popup_send_workout_pick_user, null);
        EditText usernameInput = popupView.findViewById(R.id.username_input);
        TextInputLayout usernameInputLayout = popupView.findViewById(R.id.username_input_layout);
        ListView friendsListView = popupView.findViewById(R.id.friends_list_view);
        TextView remainingToSendTv = popupView.findViewById(R.id.remaining_workouts_to_send_tv);
        int remainingAmount = Variables.MAX_FREE_WORKOUTS_SENT - user.getWorkoutsSent();
        if (remainingAmount < 0) {
            remainingAmount = 0; // lol. Just to cover my ass in case
        }
        remainingToSendTv.setText(String.format("You can share a workout %d more times.", remainingAmount));
        List<String> friendsUsernames = new ArrayList<>();
        for (String username : user.getFriends().keySet()) {
            if (user.getFriends().get(username).isConfirmed()) {
                friendsUsernames.add(username);
            }
        }
        if (friendsUsernames.isEmpty()) {
            // user has no friends, so hide the TV and listview that displays the friends
            popupView.findViewById(R.id.friends_text_view).setVisibility(View.GONE);
            friendsListView.setVisibility(View.GONE);
        }
        Collections.sort(friendsUsernames, String::compareToIgnoreCase);
        ArrayAdapter<String> friendsAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, friendsUsernames);
        friendsListView.setAdapter(friendsAdapter);
        // when user clicks on one of their friends, put that friend's username into the input
        friendsListView.setOnItemClickListener((adapterView, view, i, l) -> usernameInput.setText(friendsUsernames.get(i)));

        usernameInput.addTextChangedListener(AndroidUtils.hideErrorTextWatcher(usernameInputLayout));
        usernameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Variables.MAX_USERNAME_LENGTH)});
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle(String.format("Share \"%s\" to another user", currentWorkout.getWorkoutName()))
                .setView(popupView)
                .setPositiveButton("Share", null)
                .setNegativeButton("Cancel", null)
                .create();
        alertDialog.setOnShowListener(dialogInterface -> {
            Button shareButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            shareButton.setOnClickListener(view -> {
                String username = usernameInput.getText().toString().trim();
                String errorMsg = ValidatorUtils.validUserToSendWorkout(user.getUsername(), username);
                if (errorMsg != null) {
                    usernameInputLayout.setError(errorMsg);
                } else {
                    // no problems so go ahead and send
                    alertDialog.dismiss();
                    if (user.getPremiumToken() == null && user.getWorkoutsSent() >= Variables.MAX_FREE_WORKOUTS_SENT) {
                        AndroidUtils.showErrorDialog("Too many workouts shared", "You have reached the maximum amount of workouts allowed to share.", getContext());
                    } else {
                        shareWorkout(username, currentWorkout.getWorkoutId());
                    }
                }
            });
        });
        alertDialog.show();
    }

    private void shareWorkout(String recipientUsername, String workoutId) {
        AndroidUtils.showLoadingDialog(loadingDialog, "Sharing...");
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
                    AndroidUtils.showErrorDialog("Share Workout Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    /**
     * Prompt user if they actually want to delete the currently selected workout.
     */
    private void promptDelete() {
        String message = "Are you sure you wish to permanently delete \"" + currentWorkout.getWorkoutName() + "\"?" +
                "\n\nIf so, all statistics for it will also be deleted.";
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Delete Workout")
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> {
                    String nextWorkoutId = null;
                    if (workoutList.size() >= 2) {
                        // there's at least two elements so next workout after deleting current is the second element in current list
                        nextWorkoutId = workoutList.get(1).getWorkoutId(); // get next in list
                    }
                    deleteWorkout(currentWorkout.getWorkoutId(), nextWorkoutId);
                })
                .setNegativeButton("No", null)
                .create();
        alertDialog.show();
    }

    private void deleteWorkout(String workoutId, String nextWorkoutId) {
        AndroidUtils.showLoadingDialog(loadingDialog, "Deleting...");
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<UserWithWorkout> resultStatus = this.workoutRepository.deleteWorkoutThenFetchNext(workoutId, nextWorkoutId);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                loadingDialog.dismiss();
                if (resultStatus.isSuccess()) {
                    user.setCurrentWorkout(resultStatus.getData().getUser().getCurrentWorkout());
                    user.updateUserWorkouts(resultStatus.getData().getUser().getWorkoutMetas());
                    user.updateOwnedExercises(resultStatus.getData().getUser().getOwnedExercises());

                    userWithWorkout.setWorkout(resultStatus.getData().getWorkout());
                    currentWorkout = userWithWorkout.getWorkout();
                    if (currentWorkout == null) {
                        // means there are no workouts left, so change view to tell user to create a workout
                        resetFragment();
                    } else {
                        updateUI();
                    }
                } else {
                    AndroidUtils.showErrorDialog("Delete Workout Error", resultStatus.getErrorMessage(), getContext());
                }
            });
        });
    }

    private void switchWorkout(final WorkoutMeta selectedWorkout) {
        if (selectedWorkout.getWorkoutId().equals(currentWorkout.getWorkoutId())) {
            // don't allow user to switch to current workout since they are already on it
            return;
        }
        AndroidUtils.showLoadingDialog(loadingDialog, "Loading...");
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<UserWithWorkout> resultStatus = this.workoutRepository.switchWorkout(currentWorkout, selectedWorkout.getWorkoutId());
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                if (this.isResumed()) {
                    loadingDialog.dismiss();
                    if (resultStatus.isSuccess()) {
                        // set new active workout and update user
                        userWithWorkout.setWorkout(resultStatus.getData().getWorkout());
                        currentWorkout = userWithWorkout.getWorkout();
                        user.setCurrentWorkout(currentWorkout.getWorkoutId());
                        user.getWorkoutMetas().put(currentWorkout.getWorkoutId(),
                                resultStatus.getData().getUser().getWorkoutMetas().get(currentWorkout.getWorkoutId()));
                        updateUI();
                    } else {
                        AndroidUtils.showErrorDialog("Switch Workout Error", resultStatus.getErrorMessage(), getContext());
                        workoutListView.setItemChecked(0, true);
                    }
                }
            });
        });
    }

    /**
     * Resets the current fragment. Used when all workouts are deleted
     */
    private void resetFragment() {
        getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                new MyWorkoutsFragment(), Variables.MY_WORKOUT_TITLE).commit();
    }
}
