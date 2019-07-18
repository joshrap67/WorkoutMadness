package com.example.workoutmadness;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class NewWorkoutFragment extends Fragment {
    private boolean modified = false, firstDay, lastDay;
    private EditText workoutNameInput, numWeeksInput, numDaysInput;
    private Button previousDayBtn, nextDayBtn;
    private int finalDayNum, finalWeekNum;
    private String finalName;
    private View view, popupView;
    private AlertDialog alertDialog;
    private ArrayList<String> checkedExercises = new ArrayList<>();
    private TableLayout pickExerciseTable, displayedExercisesTable;
    private int currentDayIndex, maxDayIndex;
    private TextView dayTitle;
    private ViewGroup fragmentContainer;
    private HashMap<Integer, ArrayList<String>> exercises = new HashMap<>();


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentContainer=container;
        view = inflater.inflate(R.layout.fragment_new, container, false);
        ((MainActivity) getActivity()).updateToolbarTitle("Workout Creator");
        currentDayIndex=0;
        initViews();
        return view;
    }

    public void initViews() {
        workoutNameInput = view.findViewById(R.id.workoutNameInput);
        numWeeksInput = view.findViewById(R.id.weekInput);
        numDaysInput = view.findViewById(R.id.dayInput);
        Button nextButton = view.findViewById(R.id.nextButton);
        // TODO hide keyboard when clicking elsewhere
        workoutNameInput.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View view, int keyCode, KeyEvent keyevent) {
                if ((keyevent.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    boolean validName = checkValidName(workoutNameInput.getText().toString());
                    if (validName) {
                        modified=true;
                        return true;
                    }
                }
                return false;
            }
        });

        numWeeksInput.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View view, int keyCode, KeyEvent keyevent) {
                if ((keyevent.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    boolean validWeek = checkValidWeek(numWeeksInput.getText().toString());
                    if (validWeek) {
                        modified=true;
                        return true;
                    }
                }
                return false;
            }
        });
        numDaysInput.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View view, int keyCode, KeyEvent keyevent) {
                if ((keyevent.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    boolean validDay = checkValidDay(numDaysInput.getText().toString());
                    if (validDay) {
                        modified=true;
                        return true;
                    }
                }
                return false;
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String currentName=workoutNameInput.getText().toString();
                String currentWeeks=numWeeksInput.getText().toString();
                String currentDays=numDaysInput.getText().toString();
                boolean validName = checkValidName(currentName);
                boolean validWeeks=checkValidWeek(currentWeeks);
                boolean validDays=checkValidDay(currentDays);

                if(validName&&validWeeks&&validDays){
                    finalName=currentName.trim();
                    finalWeekNum=Integer.parseInt(currentWeeks);
                    finalDayNum=Integer.parseInt(currentDays);
                    maxDayIndex=(finalWeekNum*finalDayNum)-1;
                    createWorkout();
                }
            }
        });
    }

    public boolean checkValidName(String aName){
        aName=aName.trim();
        if((aName.length()>0)&&(aName.length()<500)){
            String[] letters = aName.split("");
            for(String letter : letters){
                if(letter.equalsIgnoreCase(".")){
                    displayErrorMessage("Name","No special characters allowed!");
                    return false;
                }
            }
            // check if workout name has already been used before
            File directoryHandle = getActivity().getExternalFilesDir(Variables.WORKOUT_DIRECTORY);
            File[] contents = directoryHandle.listFiles();
            for(File file : contents){
                if(file.getName().equalsIgnoreCase(aName+".txt")){
                    displayErrorMessage("Name","Workout name already exists!");
                    return false;
                }
            }
            return true;
        }
        displayErrorMessage("Name","Workout name has too few or too many characters!");
        return false;
    }

    public boolean checkValidWeek(String aWeek) {
        if(aWeek.length()==0){
            displayErrorMessage("Weeks","Enter value between 1-8!");
            return false;
        }
        int week = Integer.parseInt(aWeek);
        if (week > 0 && week < 9) {
            return true;
        }
        displayErrorMessage("Weeks","Enter value between 1-8!");
        return false;
    }

    public boolean checkValidDay(String aDay) {
        if(aDay.length()==0){
            displayErrorMessage("Days","Enter value between 1-7!");
            return false;
        }
        int day = Integer.parseInt(aDay);
        if (day > 0 && day < 8) {
            return true;
        }
        displayErrorMessage("Days","Enter value between 1-7!");
        return false;
    }

    public void displayErrorMessage(String editText, String msg){
        switch (editText){
            case "Name":
                workoutNameInput.setError(msg);
                workoutNameInput.setText("");
                break;
            case "Weeks":
                numWeeksInput.setError(msg);
                numWeeksInput.setText("");
                break;
            case "Days":
                numDaysInput.setError(msg);
                numDaysInput.setText("");
        }
    }

    public void createWorkout() {
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View createWorkoutView = inflater.inflate(R.layout.create_workout, fragmentContainer,false);
        ViewGroup rootView = (ViewGroup) getView();
        rootView.removeAllViews();
        rootView.addView(createWorkoutView);
        displayedExercisesTable = createWorkoutView.findViewById(R.id.main_table);
        dayTitle = createWorkoutView.findViewById(R.id.dayTextView);
        updateDayTitle();
        for(int i=0;i<=maxDayIndex;i++){
            exercises.put(i, new ArrayList<String>());
        }
        setButtons(createWorkoutView);
    }

    public void updateDayTitle(){
        /*
            Updates the day title in a cyclic fashion given from user input
         */
        int weekNum = (currentDayIndex/finalDayNum)+1;
        int dayNum = (currentDayIndex%finalDayNum)+1;
        String displayTitle="W"+weekNum+":D"+dayNum;
        dayTitle.setText(displayTitle);
    }

    public void setButtons(View _view){
        final Button addExercises = _view.findViewById(R.id.add_exercises);
        addExercises.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupExercises();
            }
        });
        firstDay=true;
        previousDayBtn=_view.findViewById(R.id.previousDayButton);
        nextDayBtn=_view.findViewById(R.id.nextDayButton);
        previousDayBtn.setVisibility(View.INVISIBLE);
        if(maxDayIndex==0){
            // in case some jabroni only wants to workout one day total
            nextDayBtn.setText("FINISH");
        }
        previousDayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentDayIndex>0){
                    displayedExercisesTable.removeAllViews();
                    currentDayIndex--;
                }
                if(lastDay){
                    lastDay=false;
                    nextDayBtn.setText("NEXT");
                }

                if(currentDayIndex==0){
                    previousDayBtn.setVisibility(View.INVISIBLE);
                    firstDay=true;
                }
                addExercises();
                updateDayTitle();
            }
        });
        nextDayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentDayIndex<maxDayIndex) {
                    displayedExercisesTable.removeAllViews();
                    currentDayIndex++;
                    if(firstDay){
                        firstDay=false;
                        previousDayBtn.setVisibility(View.VISIBLE);
                    }
                    if(currentDayIndex==maxDayIndex){
                        lastDay=true;
                        nextDayBtn.setText("FINISH");
                    }
                    addExercises();
                    updateDayTitle();
                }
                else{
                    // on the last day so check if every day has at least one exercise in it before writing to file
                    boolean ready = true;
                    for(int i=0;i<exercises.size();i++){
                        if(exercises.get(i)==null){
                            ready =false;
                        }
                        else if(exercises.get(i).isEmpty()){
                            ready=false;
                        }
                    }
                    if(ready){
                        writeToFile();
                        modified=false;
                        Toast.makeText(getContext(), "Workout successfully created!",Toast.LENGTH_SHORT).show();
                        // restart this fragment
                        Fragment frag;
                        frag = getFragmentManager().findFragmentByTag("NEW_WORKOUT");
                        final FragmentTransaction ft = getFragmentManager().beginTransaction();
                        ft.detach(frag);
                        ft.attach(frag);
                        ft.commit();
                    }
                    else{
                        Toast.makeText(getContext(),"Ensure each day has at least one exercise!",Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    public void writeToFile(){
        /*
            Updates the workout file to include the changes that were made by the user. This
            is called whenever the user clicks to go to another day or exits out of the fragment.
         */
        BufferedWriter writer = null;
        File fhandle = new File(getContext().getExternalFilesDir(Variables.WORKOUT_DIRECTORY), finalName+Variables.WORKOUT_EXT);
        try{
            writer = new BufferedWriter(new FileWriter(fhandle,false));
            for(int i=0;i<maxDayIndex;i++){
                int weekNum = (i/finalDayNum)+1;
                int dayNum = (i%finalDayNum)+1;
                String dayTitle=Variables.DAY_DELIM+"*"+"W"+weekNum+":D"+dayNum+"\n";
                writer.write(dayTitle);
                for(String exercise : exercises.get(i)){
                    // TODO pull video from the video file
                    String exerciseLine = exercise+"*"+"INCOMPLETE"+"*"+"NONE\n";
                    writer.write(exerciseLine);
                }
            }
            // TODO find different way to do this that isn't hacky
            // prevents extra new line character being put at end of file
            int weekNum = (maxDayIndex/finalDayNum)+1;
            int dayNum = (maxDayIndex%finalDayNum)+1;
            String dayTitle=Variables.DAY_DELIM+"*"+"W"+weekNum+":D"+dayNum+"\n";
            writer.write(dayTitle);
            for(String exercise : exercises.get(maxDayIndex)){
                // TODO pull video from the video file
                String exerciseLine = exercise+"*"+"INCOMPLETE"+"*"+"NONE\n";
                writer.write(exerciseLine);
            }
            writer.close();
            updateCurrentWorkoutLog(finalName+Variables.WORKOUT_EXT);
        }
        catch (Exception e){
            Log.d("ERROR","Error when trying to record to workout file!\n"+e);
        }
    }

    public void addExercises(){
        Collections.sort(exercises.get(currentDayIndex));
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        int count = 0;
        for (final String exercise : exercises.get(currentDayIndex)){
            final View row = inflater.inflate(R.layout.list_row,null);
            TextView exerciseName = row.findViewById(R.id.exercise_name);
            exerciseName.setText(exercise);
            ImageView deleteIcon = row.findViewById(R.id.delete_exercise);
            deleteIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    displayedExercisesTable.removeView(row);
                    exercises.get(currentDayIndex).remove(exercise);
                }
            });
            displayedExercisesTable.addView(row,count);
            count++;
        }
    }

    public void popupExercises(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialog = alertDialogBuilder.create();
        popupView = getLayoutInflater().inflate(R.layout.exercise_popup, null);
        Spinner clusterSpinner=popupView.findViewById(R.id.clusterSpinner);
        pickExerciseTable = popupView.findViewById(R.id.main_table);
        clusterSpinner.setOnItemSelectedListener(new SpinnerListener());
        alertDialog.setView(popupView);
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.show();
        ImageView done = popupView.findViewById(R.id.imageView);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // done adding
                if(checkedExercises.isEmpty()){
                    alertDialog.dismiss();
                    return;
                }
                for(String exercise : checkedExercises){
                    exercises.get(currentDayIndex).add(exercise);
                }
                checkedExercises.clear();
                displayedExercisesTable.removeAllViews();
                addExercises();
                alertDialog.dismiss();
            }
        });
    }

    public void updateExerciseChoices(String exerciseCluster){
        String[] exerciseValues;
        switch (exerciseCluster){
            case "Chest":
                exerciseValues=getContext().getResources().getStringArray(R.array.chest_day);
                break;
            case "Legs":
                exerciseValues=getContext().getResources().getStringArray(R.array.leg_day);
                break;
            default:
                exerciseValues = new String[] {"somebody","once","told","me"};
        }
        ArrayList<String> sortedExercises = new ArrayList<>();
        Collections.addAll(sortedExercises,exerciseValues);
        Collections.sort(sortedExercises);
        for(int i=0;i<exerciseValues.length;i++){
            TableRow row = new TableRow(getActivity());
            TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(lp);
            final CheckBox exercise = new CheckBox(getActivity());
            String exerciseName = sortedExercises.get(i);
            exercise.setText(exerciseName);
            if(checkedExercises.contains(exerciseName) || exercises.get(currentDayIndex).contains(exerciseName)){
                exercise.setChecked(true);
            }
            exercise.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(!checkedExercises.contains(exercise.getText().toString())&&
                    !exercises.get(currentDayIndex).contains(exercise.getText().toString())){
                        // prevents exercise from being added twice
                        checkedExercises.add(exercise.getText().toString());
                    }
                    else{
                        checkedExercises.remove(exercise.getText().toString());
                    }

                }
            });
            row.addView(exercise);
            pickExerciseTable.addView(row,i);
        }
    }

    public void updateCurrentWorkoutLog(String workoutName){
        // structure of the log is that the first line is the current workout
        BufferedReader reader = null;
        BufferedWriter writer = null;
        String _data = null;
        File fhandleOld = new File(getContext().getExternalFilesDir(Variables.WORKOUT_DIRECTORY), Variables.CURRENT_WORKOUT_LOG);
        File fhandleNew = new File(getContext().getExternalFilesDir(Variables.WORKOUT_DIRECTORY), "temp");
        try{
            // progress through the file until the correct spot is found
            writer = new BufferedWriter(new FileWriter(fhandleNew,true));
            FileReader fileR= new FileReader(fhandleOld);
            reader = new BufferedReader(fileR);
            String line;
            while((line=reader.readLine())!=null){
                writer.write(line+"\n");
            }
            // add the new workout to the log and start it at day 0
            writer.write(workoutName+"*0");
            reader.close();
            writer.close();
            fhandleOld.delete();
            fhandleNew.renameTo(fhandleOld);
        }
        catch (Exception e){
            Log.d("ERROR","Error when trying to update current workout log!\n"+e);
        }
    }

    public boolean isModified() {
        /*
            Is used to check if the user has made any at all changes to their workout. If so, appropriate
            action (namely altering the text file) must be taken.
         */
        return modified;
    }

    public void setModified(boolean status){
        modified=status;
    }

    private class SpinnerListener implements AdapterView.OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            String selectedExerciseCluster = parent.getItemAtPosition(pos).toString();
            pickExerciseTable.removeAllViews();
            updateExerciseChoices(selectedExerciseCluster);
        }

        public void onNothingSelected(AdapterView parent) {
        }
    }

}
