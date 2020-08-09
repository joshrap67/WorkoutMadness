package com.joshrap.liteweight.fragments;

import android.app.AlertDialog;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.joshrap.liteweight.*;
import com.joshrap.liteweight.activities.WorkoutActivity;
import com.joshrap.liteweight.adapters.CustomSortAdapter;
import com.joshrap.liteweight.adapters.PendingRoutineAdapter;
import com.joshrap.liteweight.helpers.InputHelper;
import com.joshrap.liteweight.helpers.WorkoutHelper;
import com.joshrap.liteweight.imports.Globals;
import com.joshrap.liteweight.imports.Variables;
import com.joshrap.liteweight.interfaces.FragmentWithDialog;
import com.joshrap.liteweight.models.ExerciseRoutine;
import com.joshrap.liteweight.models.ExerciseUser;
import com.joshrap.liteweight.models.ResultStatus;
import com.joshrap.liteweight.models.Routine;
import com.joshrap.liteweight.models.RoutineDayMap;
import com.joshrap.liteweight.models.User;
import com.joshrap.liteweight.models.UserWithWorkout;
import com.joshrap.liteweight.network.CognitoGateway;
import com.joshrap.liteweight.network.repos.WorkoutRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.os.Looper.getMainLooper;

public class NewWorkoutFragment extends Fragment implements FragmentWithDialog {
    private RecyclerView routineRecyclerView;
    private AlertDialog alertDialog;
    private RecyclerView pickExerciseRecyclerView;
    private TextView dayTitleTV, exerciseNotFoundTV;
    private String spinnerFocus;
    private HashMap<String, ArrayList<ExerciseUser>> allExercises;
    private Routine pendingRoutine;
    private List<String> weekSpinnerValues;
    private List<String> daySpinnerValues;
    private int currentWeekIndex;
    private int currentDayIndex;
    private ArrayAdapter<String> weekAdapter;
    private ArrayAdapter<String> dayAdapter;
    private Spinner weekSpinner;
    private Spinner daySpinner;
    private User activeUser;
    private TextView emptyView;
    private Button dayButton, weekButton;
    private boolean addMode;
    private Map<String, String> exerciseIdToName;
    private ImageButton sortButton;
    private FloatingActionButton addExercisesButton;
    private LinearLayout radioLayout, customSortLayout;
    private Button saveButton;
    private RelativeLayout relativeLayout;
    private AddExerciseAdapter addExerciseAdapter;
    private ProgressDialog loadingDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_new_workout, container, false);
        ((WorkoutActivity) getActivity()).enableBackButton(true);
        ((WorkoutActivity) getActivity()).updateToolbarTitle(Variables.NEW_WORKOUT_TITLE);
        pendingRoutine = new Routine();
        pendingRoutine.appendNewDay(0, 0);
        weekSpinnerValues = new ArrayList<>();
        loadingDialog = new ProgressDialog(getActivity());
        loadingDialog.setCancelable(false);
        allExercises = new HashMap<>();
        daySpinnerValues = new ArrayList<>();
        activeUser = Globals.user; // TODO dependency injection?

        exerciseIdToName = new HashMap<>();
        for (String id : activeUser.getUserExercises().keySet()) {
            exerciseIdToName.put(id, activeUser.getUserExercises().get(id).getExerciseName());
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        /*
            Init all views and buttons when view is loaded onto screen
         */
        currentDayIndex = 0;
        currentWeekIndex = 0;
        routineRecyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        dayTitleTV = view.findViewById(R.id.day_text_view);
        weekButton = view.findViewById(R.id.add_week_btn);
        dayButton = view.findViewById(R.id.add_day_btn);
        customSortLayout = view.findViewById(R.id.custom_sort_layout);
        Button saveSortButton = view.findViewById(R.id.done_sorting_btn);
        radioLayout = view.findViewById(R.id.mode_linear_layout);
        relativeLayout = view.findViewById(R.id.relative_layout);
        saveButton = view.findViewById(R.id.save_button);

        addMode = true;
        dayTitleTV.setText(WorkoutHelper.generateDayTitleNew(currentWeekIndex, currentDayIndex));
        setSpinners(view);
        setButtons();
        updateButtonTexts();
        updateRoutineListUI();
        saveSortButton.setOnClickListener(v -> {
            customSortLayout.setVisibility(View.GONE);
            saveButton.setVisibility(View.VISIBLE);
            sortButton.setVisibility(View.VISIBLE);
            addExercisesButton.show();
            relativeLayout.setVisibility(View.VISIBLE);
            radioLayout.setVisibility(View.VISIBLE);
            updateRoutineListUI();
            // needed to avoid weird bug that happens when user tries to sort again by dragging
            itemTouchHelper.attachToRecyclerView(null);
        });

        RadioButton addRadioButton = view.findViewById(R.id.add_radio_btn);
        addRadioButton.setOnClickListener(v -> {
            if (!addMode) {
                // prevent useless function call if already in this mode
                addMode = true;
                setButtons();
                updateButtonTexts();
            }
        });
        RadioButton deleteRadioButton = view.findViewById(R.id.delete_radio_btn);
        deleteRadioButton.setOnClickListener(v -> {
            if (addMode) {
                // prevent useless function call if already in this mode
                addMode = false;
                setButtons();
                updateButtonTexts();
            }
        });
        addExercisesButton = view.findViewById(R.id.add_exercises);
        addExercisesButton.setOnClickListener(v -> popupAddExercises());
        sortButton = view.findViewById(R.id.sort_button);
        final PopupMenu dropDownMenu = new PopupMenu(getContext(), sortButton);
        final Menu menu = dropDownMenu.getMenu();
        menu.add(0, RoutineDayMap.alphabeticalSortAscending, 0, "Sort Alphabetical (A-Z)");
        menu.add(0, RoutineDayMap.alphabeticalSortDescending, 0, "Sort Alphabetical (Z-A)");
        menu.add(0, RoutineDayMap.weightSortAscending, 0, "Sort by Weight (Ascending)");
        menu.add(0, RoutineDayMap.weightSortDescending, 0, "Sort by Weight (Descending)");
        menu.add(0, RoutineDayMap.customSort, 0, "Custom Sort (Drag 'n Drop)");

        dropDownMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case RoutineDayMap.alphabeticalSortAscending:
                    this.pendingRoutine.sortDay(currentWeekIndex, currentDayIndex, RoutineDayMap.alphabeticalSortAscending, exerciseIdToName);
                    updateRoutineListUI();
                    return true;
                case RoutineDayMap.alphabeticalSortDescending:
                    this.pendingRoutine.sortDay(currentWeekIndex, currentDayIndex, RoutineDayMap.alphabeticalSortDescending, exerciseIdToName);
                    updateRoutineListUI();
                    return true;
                case RoutineDayMap.weightSortDescending:
                    this.pendingRoutine.sortDay(currentWeekIndex, currentDayIndex, RoutineDayMap.weightSortDescending, exerciseIdToName);
                    updateRoutineListUI();
                    return true;
                case RoutineDayMap.weightSortAscending:
                    this.pendingRoutine.sortDay(currentWeekIndex, currentDayIndex, RoutineDayMap.weightSortAscending, exerciseIdToName);
                    updateRoutineListUI();
                    return true;
                case RoutineDayMap.customSort:
                    if (pendingRoutine.getExerciseListForDay(currentWeekIndex, currentDayIndex).isEmpty()) {
                        Toast.makeText(getContext(), "Add at least one exercise.", Toast.LENGTH_LONG).show();
                    } else {
                        customSort();
                    }
                    return true;
            }
            return false;
        });
        sortButton.setOnClickListener(v -> dropDownMenu.show());
        super.onViewCreated(view, savedInstanceState);
    }

    private void setButtons() {
        /*
            Set the onclicklisteners of the week and add button. If add mode is active the user can add weeks/days
         */
        weekButton.setOnClickListener((v -> {
            if (addMode) {
                currentDayIndex = 0;
                // for now only allow for weeks to be appended not insert
                currentWeekIndex = pendingRoutine.getRoutine().size();
                pendingRoutine.appendNewDay(currentWeekIndex, currentDayIndex);
                weekSpinner.setSelection(currentWeekIndex);
                daySpinner.setSelection(0);
                updateWeekSpinnerValues();
                updateDaySpinnerValues();
                weekAdapter.notifyDataSetChanged();
                dayAdapter.notifyDataSetChanged();
                dayTitleTV.setText(WorkoutHelper.generateDayTitleNew(currentWeekIndex, currentDayIndex));
                updateRoutineListUI();
                updateButtonTexts();
            } else {
                promptDeleteWeek();
            }

        }));
        saveButton.setOnClickListener(v -> {
            boolean validRoutine = true;
            for (Integer week : pendingRoutine.getRoutine().keySet()) {
                for (Integer day : pendingRoutine.getRoutine().get(week).keySet()) {
                    if (pendingRoutine.getExerciseListForDay(week, day).isEmpty()) {
                        validRoutine = false;
                    }
                }
            }
            if (validRoutine) {
                promptSave();
            } else {
                Toast.makeText(getContext(), "Each day must have at least one exercise!", Toast.LENGTH_LONG).show();
            }
        });
        dayButton.setOnClickListener((v -> {
            if (addMode) {
                // for now only allow for weeks to be appended not insert
                currentDayIndex = pendingRoutine.getRoutine().get(currentWeekIndex).size();
                pendingRoutine.appendNewDay(currentWeekIndex, currentDayIndex);
                updateWeekSpinnerValues();
                updateDaySpinnerValues();
                daySpinner.setSelection(currentDayIndex);
                dayAdapter.notifyDataSetChanged();
                dayTitleTV.setText(WorkoutHelper.generateDayTitleNew(currentWeekIndex, currentDayIndex));
                updateRoutineListUI();
                updateButtonTexts();
            } else {
                promptDeleteDay();
            }
        }));
    }

    private void updateButtonTexts() {
        /*
            Updates the button texts of the week/day buttons depending on the current mode
         */
        if (addMode) {
            if (this.pendingRoutine.getRoutine().keySet().size() >= Variables.MAX_NUMBER_OF_WEEKS) {
                weekButton.setText(getString(R.string.max_reached_msg));
                weekButton.setEnabled(false);
            } else {
                weekButton.setText(getString(R.string.add_week_msg));
                weekButton.setEnabled(true);
            }
            if (this.pendingRoutine.getRoutine().get(currentWeekIndex).keySet().size() >=
                    Variables.FIXED_WORKOUT_MAX_NUMBER_OF_DAYS) {
                dayButton.setText(getString(R.string.max_reached_msg));
                dayButton.setEnabled(false);
            } else {
                dayButton.setText(getString(R.string.add_day_msg));
                dayButton.setEnabled(true);
            }
        } else {
            dayButton.setEnabled(true);
            weekButton.setEnabled(true);
            dayButton.setText(getString(R.string.remove_day_msg));
            weekButton.setText(getString(R.string.remove_week_msg));
        }
    }

    private void setSpinners(View view) {
        /*
            Enable the spinners to provide a means of navigating the routine by week and day
         */
        // setup the week spinner
        weekAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, weekSpinnerValues);
        weekAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        weekSpinner = view.findViewById(R.id.week_spinner);
        weekSpinner.setAdapter(weekAdapter);
        weekSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentWeekIndex = position;
                currentDayIndex = 0;
                daySpinner.setSelection(0);
                updateDaySpinnerValues();
                updateRoutineListUI();
                dayTitleTV.setText(WorkoutHelper.generateDayTitleNew(currentWeekIndex, currentDayIndex));
                updateButtonTexts();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // setup the day spinner
        dayAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, daySpinnerValues);
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        daySpinner = view.findViewById(R.id.day_spinner);
        daySpinner.setAdapter(dayAdapter);
        daySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentDayIndex = position;
                updateRoutineListUI();
                dayTitleTV.setText(WorkoutHelper.generateDayTitleNew(currentWeekIndex, currentDayIndex));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        updateWeekSpinnerValues();
        updateDaySpinnerValues();
    }

    private void updateWeekSpinnerValues() {
        /*
            Update spinner list with all the available weeks to progress
         */
        weekSpinnerValues.clear();
        for (int i = 0; i < pendingRoutine.getRoutine().keySet().size(); i++) {
            weekSpinnerValues.add("Week " + (i + 1));
        }
        weekAdapter.notifyDataSetChanged();
    }

    private void updateDaySpinnerValues() {
        /*
            Update spinner list with all the available day to progress
         */
        daySpinnerValues.clear();
        for (int i = 0; i < pendingRoutine.getRoutine().get(currentWeekIndex).size(); i++) {
            daySpinnerValues.add("Day " + (i + 1));
        }
        dayAdapter.notifyDataSetChanged();
    }

    @Override
    public void hideAllDialogs() {
        /*
            Close any dialogs that might be showing. This is essential when clicking a notification that takes
            the user to a new page.
         */
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void updateRoutineListUI() {
        /*
            Updates the list of displayed exercises in the workout depending on the current day.
         */

        PendingRoutineAdapter routineAdapter = new PendingRoutineAdapter
                (pendingRoutine.getExerciseListForDay(currentWeekIndex, currentDayIndex),
                        exerciseIdToName, pendingRoutine, currentWeekIndex, currentDayIndex,
                        false);
        routineAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            // since google is stupid af and doesn't have a simple setEmptyView for recyclerView...
            @Override
            public void onChanged() {
                super.onChanged();
                checkEmpty();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                checkEmpty();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);
                checkEmpty();
            }
        });
        routineRecyclerView.setAdapter(routineAdapter);
        routineRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        dayTitleTV.setText(WorkoutHelper.generateDayTitleNew(currentWeekIndex, currentDayIndex));
        checkEmpty();
    }

    private void customSort() {
        customSortLayout.setVisibility(View.VISIBLE);
        saveButton.setVisibility(View.GONE);
        sortButton.setVisibility(View.GONE);
        relativeLayout.setVisibility(View.GONE);
        addExercisesButton.hide();
        radioLayout.setVisibility(View.GONE);

        CustomSortAdapter routineAdapter = new CustomSortAdapter(
                pendingRoutine.getExerciseListForDay(currentWeekIndex, currentDayIndex),
                exerciseIdToName, false);

        itemTouchHelper.attachToRecyclerView(routineRecyclerView);
        routineRecyclerView.setAdapter(routineAdapter);
    }

    private ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder dragged, @NonNull RecyclerView.ViewHolder target) {
            int fromPosition = dragged.getAdapterPosition();
            int toPosition = target.getAdapterPosition();
            pendingRoutine.swapExerciseOrder(currentWeekIndex, currentDayIndex, fromPosition, toPosition);
            recyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

        }
    });


    private void promptDeleteWeek() {
        String message = "Are you sure you wish to delete week " + (currentWeekIndex + 1) + "?\n\n" +
                "Doing so will delete ALL days associated with this week, and this action cannot be reversed!";
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Delete Week " + (currentWeekIndex + 1))
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> {
                    if (pendingRoutine.getRoutine().keySet().size() > 1) {
                        deleteWeek();
                        alertDialog.dismiss();
                    } else {
                        // don't allow for week to be deleted if it is the only one
                        Toast.makeText(getContext(), "Cannot delete only week from routine.", Toast.LENGTH_LONG).show();
                        alertDialog.dismiss();
                    }

                })
                .setNegativeButton("No", null)
                .create();
        alertDialog.show();
        // make the message font a little bigger than the default one provided by the alertdialog
        TextView messageTV = alertDialog.getWindow().findViewById(android.R.id.message);
        messageTV.setTextSize(18);
    }

    private void promptDeleteDay() {
        String message = "Are you sure you wish to delete day " + (currentDayIndex + 1) + "?\n\n" +
                "Doing so will delete ALL exercises associated with this day, and this action cannot be reversed!";
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Delete Day " + (currentDayIndex + 1))
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> {
                    if (pendingRoutine.getRoutine().get(currentWeekIndex).keySet().size() > 1) {
                        deleteDay();
                        alertDialog.dismiss();
                    } else {
                        // don't allow for week to be deleted if it is the only one
                        Toast.makeText(getContext(), "Cannot delete only day from routine.", Toast.LENGTH_LONG).show();
                        alertDialog.dismiss();
                    }

                })
                .setNegativeButton("No", null)
                .create();
        alertDialog.show();
        // make the message font a little bigger than the default one provided by the alertdialog
        TextView messageTV = alertDialog.getWindow().findViewById(android.R.id.message);
        messageTV.setTextSize(18);
    }

    private void promptSave() {
        View popupView = getActivity().getLayoutInflater().inflate(R.layout.popup_save_workout, null);
        final EditText workoutNameInput = popupView.findViewById(R.id.workout_name_input);
        final TextInputLayout workoutNameInputLayout = popupView.findViewById(R.id.workout_name_input_layout);
        workoutNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (workoutNameInputLayout.isErrorEnabled()) {
                    // if an error is present, stop showing the error message once the user types (acknowledged it)
                    workoutNameInputLayout.setErrorEnabled(false);
                    workoutNameInputLayout.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        workoutNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Variables.MAX_WORKOUT_NAME)});
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Save workout")
                .setView(popupView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        alertDialog.setCancelable(false);
        alertDialog.setOnShowListener(dialogInterface -> {
            Button saveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(view -> {
                String workoutName = workoutNameInput.getText().toString().trim();
                List<String> workoutNames = new ArrayList<>();
                for (String workoutId : activeUser.getUserWorkouts().keySet()) {
                    workoutNames.add(activeUser.getUserWorkouts().get(workoutId).getWorkoutName());
                }
                String errorMsg = InputHelper.validWorkoutName(workoutName, workoutNames);
                if (errorMsg != null) {
                    workoutNameInputLayout.setError(errorMsg);
                } else {
                    // no problems so go ahead and save
                    alertDialog.dismiss();
                    createWorkout(workoutName);
                }
            });
        });
        alertDialog.show();
    }

    private void createWorkout(String workoutName) {
        showLoadingDialog();
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ResultStatus<UserWithWorkout> resultStatus = WorkoutRepository.createWorkout(pendingRoutine, workoutName);
            Handler handler = new Handler(getMainLooper());
            handler.post(() -> {
                loadingDialog.dismiss();
                if (resultStatus.isSuccess()) {
                    // TODO save and what not
                    System.out.println(resultStatus.getData());
                } else {
                    showErrorMessage(resultStatus.getErrorMessage());
                }
            });
        });
    }

    private void showLoadingDialog() {
        loadingDialog.setMessage("Saving...");
        loadingDialog.show();
    }

    private void showErrorMessage(String message) {
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Save workout error")
                .setMessage(message)
                .setPositiveButton("Ok", null)
                .create();
        alertDialog.show();
        // make the message font a little bigger than the default one provided by the alertdialog
        TextView messageTV = alertDialog.getWindow().findViewById(android.R.id.message);
        messageTV.setTextSize(18);
    }

    private void deleteDay() {
        pendingRoutine.deleteDay(currentWeekIndex, currentDayIndex);

        if (currentDayIndex != 0) {
            // if on the first day, then move the user forward to the old day 2
            currentDayIndex--;
        }
        daySpinner.setSelection(currentDayIndex);
        updateWeekSpinnerValues();
        updateDaySpinnerValues();
        weekAdapter.notifyDataSetChanged();
        dayAdapter.notifyDataSetChanged();
        updateRoutineListUI();
        updateButtonTexts();
    }

    private void deleteWeek() {
        pendingRoutine.deleteWeek(currentWeekIndex);

        if (currentWeekIndex != 0) {
            // if on the first week, then move the user forward to the old week 2
            currentWeekIndex--;
        }
        currentDayIndex = 0;
        daySpinner.setSelection(0);
        weekSpinner.setSelection(currentWeekIndex);
        updateWeekSpinnerValues();
        updateDaySpinnerValues();
        weekAdapter.notifyDataSetChanged();
        dayAdapter.notifyDataSetChanged();
        dayTitleTV.setText(WorkoutHelper.generateDayTitleNew(currentWeekIndex, currentDayIndex));
        updateRoutineListUI();
        updateButtonTexts();
    }

    private void checkEmpty() {
        /*
            Used to check if the specific day has exercises in it or not. If not, show a textview alerting user
         */
        emptyView.setVisibility(pendingRoutine.getExerciseListForDay(currentWeekIndex, currentDayIndex).isEmpty()
                ? View.VISIBLE : View.GONE);
    }

    private void popupAddExercises() {
        /*
            User has indicated they wish to add exercises to this specific day. Show a popup that provides a spinner
            that is programmed to list all exercises for a given exercise focus.
         */
        View popupView = getLayoutInflater().inflate(R.layout.popup_add_exercise_new, null);
        pickExerciseRecyclerView = popupView.findViewById(R.id.pick_exercises_recycler_view);
        exerciseNotFoundTV = popupView.findViewById(R.id.search_not_found_TV);
        final Spinner focusSpinner = popupView.findViewById(R.id.focus_spinner);
        allExercises = new HashMap<>();
        ArrayList<String> focusList = new ArrayList<>();
        for (String exerciseId : activeUser.getUserExercises().keySet()) {
            ExerciseUser exerciseUser = activeUser.getUserExercises().get(exerciseId);
            Map<String, Boolean> focuses = exerciseUser.getFocuses();
            for (String focus : focuses.keySet()) {
                if (!allExercises.containsKey(focus)) {
                    // focus hasn't been added before
                    focusList.add(focus);
                    allExercises.put(focus, new ArrayList<>());
                }
                allExercises.get(focus).add(exerciseUser);
            }
        }

        SearchView searchView = popupView.findViewById(R.id.search_input);
        searchView.setOnSearchClickListener(v -> {
            // populate the list view with all exercises
            ArrayList<ExerciseUser> sortedExercises = new ArrayList<>();
            for (String focus : allExercises.keySet()) {
                for (ExerciseUser exercise : allExercises.get(focus)) {
                    if (!sortedExercises.contains(exercise)) {
                        sortedExercises.add(exercise);
                    }
                }
            }
            Collections.sort(sortedExercises);
            addExerciseAdapter = new AddExerciseAdapter(sortedExercises);
            pickExerciseRecyclerView.setAdapter(addExerciseAdapter);
            pickExerciseRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

            focusSpinner.setVisibility(View.GONE);
        });
        searchView.setOnCloseListener(() -> {
            focusSpinner.setVisibility(View.VISIBLE);
            exerciseNotFoundTV.setVisibility(View.GONE);
            updateAddExerciseChoices();
            return false;
        });

        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                addExerciseAdapter.getFilter().filter(newText);
                return false;
            }
        });

        Collections.sort(focusList, String.CASE_INSENSITIVE_ORDER);
        ArrayAdapter<String> focusAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_dropdown_item, focusList);
        focusSpinner.setAdapter(focusAdapter);
        focusSpinner.setOnItemSelectedListener(new FocusSpinnerListener());
        // initially select first item from spinner, then always select the one the user last clicked
        focusSpinner.setSelection((spinnerFocus == null) ? 0 : focusList.indexOf(spinnerFocus));
        // view is all set up, so now create the dialog with it
        alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setTitle("Add Exercises")
                .setView(popupView)
                .setPositiveButton("Done", null)
                .create();
        alertDialog.show();
    }

    private void updateAddExerciseChoices() {
        /*
            Given the current focus spinner value, list all the exercises associated with it.
         */
        ArrayList<ExerciseUser> sortedExercises = new ArrayList<>(allExercises.get(spinnerFocus));
        Collections.sort(sortedExercises);
        addExerciseAdapter = new AddExerciseAdapter(sortedExercises);
        pickExerciseRecyclerView.setAdapter(addExerciseAdapter);
        pickExerciseRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private class FocusSpinnerListener implements AdapterView.OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            spinnerFocus = parent.getItemAtPosition(pos).toString();
            updateAddExerciseChoices();
        }

        public void onNothingSelected(AdapterView parent) {
        }
    }

    private class AddExerciseAdapter extends
            RecyclerView.Adapter<AddExerciseAdapter.ViewHolder> implements Filterable {
        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox exercise;

            ViewHolder(View itemView) {
                super(itemView);
                exercise = itemView.findViewById(R.id.exercise_checkbox);
            }
        }

        private List<ExerciseUser> exercises;
        private List<ExerciseUser> displayList;

        AddExerciseAdapter(List<ExerciseUser> exerciseRoutines) {
            this.exercises = exerciseRoutines;
            displayList = new ArrayList<>(this.exercises);
        }


        @Override
        public AddExerciseAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            View exerciseView = inflater.inflate(R.layout.row_add_exercise, parent, false);
            return new ViewHolder(exerciseView);
        }

        @Override
        public void onBindViewHolder(AddExerciseAdapter.ViewHolder holder, int position) {
            final ExerciseUser exerciseUser = displayList.get(position);
            final CheckBox exercise = holder.exercise;
            exercise.setText(exerciseUser.getExerciseName());
            // check if the exercise is already in this specific day
            boolean isChecked = false;
            for (ExerciseRoutine exerciseRoutine : pendingRoutine.getExerciseListForDay(currentWeekIndex, currentDayIndex)) {
                if (exerciseRoutine.getExerciseId().equals(exerciseUser.getExerciseId())) {
                    isChecked = true;
                    break;
                }
            }
            exercise.setChecked(isChecked);

            exercise.setOnClickListener(v -> {
                if (exercise.isChecked()) {
                    pendingRoutine.insertExercise(currentWeekIndex, currentDayIndex,
                            new ExerciseRoutine(exerciseUser, exerciseUser.getExerciseId()));
                } else {
                    pendingRoutine.removeExercise(currentWeekIndex, currentDayIndex, exerciseUser.getExerciseId());
                }
                updateRoutineListUI();
            });
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        @Override
        public Filter getFilter() {
            return exerciseSearchFilter;
        }

        private Filter exerciseSearchFilter = new Filter() {
            // responsible for filtering the search of the user in the add user popup (by exercise name)
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<ExerciseUser> filteredList = new ArrayList<>();
                if (constraint == null || constraint.length() == 0) {
                    filteredList.addAll(exercises);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();
                    for (ExerciseUser exerciseUser : exercises) {
                        if (exerciseUser.getExerciseName().toLowerCase().contains(filterPattern)) {
                            filteredList.add(exerciseUser);
                        }
                    }
                }
                FilterResults results = new FilterResults();
                results.values = filteredList;
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                displayList.clear();
                displayList.addAll((List) results.values);
                if (displayList.isEmpty()) {
                    exerciseNotFoundTV.setVisibility(View.VISIBLE);
                } else {
                    exerciseNotFoundTV.setVisibility(View.GONE);
                }
                notifyDataSetChanged();
            }
        };
    }
}
