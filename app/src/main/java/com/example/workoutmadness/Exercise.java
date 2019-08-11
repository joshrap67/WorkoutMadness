package com.example.workoutmadness;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.example.workoutmadness.Database.Entities.ExerciseEntity;
import com.example.workoutmadness.Database.Entities.WorkoutEntity;
import com.example.workoutmadness.Database.ViewModels.ExerciseViewModel;
import com.example.workoutmadness.Database.ViewModels.WorkoutViewModel;
import com.example.workoutmadness.Fragments.*;
import android.widget.Toast;

public class Exercise{
    private Context context;
    private Activity activity;
    private String name, videoURL;
    private boolean status, videos, ignoreWeight, metricUnits;
    private Fragment fragment;
    private WorkoutViewModel workoutViewModel;
    private ExerciseViewModel exerciseViewModel;
    private WorkoutEntity workoutEntity;
    private ExerciseEntity exerciseEntity;
    private double weight;
    private String formattedWeight;

    public Exercise(final String[] rawText, Context aContext, Activity anActivity, Fragment aFragment, boolean videosEnabled, String URL){
        /*
            Constructor utilized by the current workout fragment
         */
        context = aContext;
        activity = anActivity;
        fragment = aFragment;
        videos = videosEnabled;
        if(rawText[Variables.STATUS_INDEX].equals(Variables.EXERCISE_COMPLETE)){
            // means that the exercise has already been done, so make sure to set status as so
            if(fragment instanceof CurrentWorkoutFragment){
                ((CurrentWorkoutFragment) fragment).setPreviouslyModified(true);
            }
            status=true;
        }
        else{
            status=false;
        }
        name = rawText[Variables.NAME_INDEX];
        videoURL = URL;
    }
    public Exercise(final WorkoutEntity workoutEntity, ExerciseEntity exerciseEntity, Context context, Activity activity,
                    Fragment fragment, boolean videos, boolean metricUnits, double weight, WorkoutViewModel workoutViewModel,
                    ExerciseViewModel exerciseViewModel){
        /*
            Constructor utilized for database stuff
         */
        this.workoutEntity = workoutEntity;
        this.exerciseEntity = exerciseEntity;
        this.context = context;
        this.activity = activity;
        this.fragment = fragment;
        this.videos = videos;
        this.metricUnits = metricUnits;
        this.workoutViewModel = workoutViewModel;
        this.exerciseViewModel = exerciseViewModel;
        this.weight = weight;
        if(workoutEntity.getStatus()){
            if(fragment instanceof CurrentWorkoutFragment){
                ((CurrentWorkoutFragment) fragment).setPreviouslyModified(true);
            }
            this.status = true;
        }
        else{
            this.status = false;
        }
        this.name = workoutEntity.getExercise();
        if(exerciseEntity.getUrl()!=null){
            // TODO also do error checking here to see if it's a valid url
            this.videoURL = exerciseEntity.getUrl();
        }
        else{
            this.videoURL = "NONE";
        }
    }

    public Exercise(String exerciseName){
        /*
            Constructor utilized by the new workout fragment when writing to a file
         */
        name = exerciseName;
        status = false;
    }

    public void setStatus(boolean aStatus){
            /*
                Sets the status of the exercise as either being complete or incomplete.
             */
        status = aStatus;
        workoutEntity.setStatus(aStatus);
    }

    public WorkoutEntity getWorkoutEntity(){
        return this.workoutEntity;
    }

    public String getName(){
        return this.name;
    }

    public View getDisplayedRow(){
            /*
                Takes all of the information from the instance variables of this exercise and puts it into a row to be displayed
                by the main table.
             */
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View row = inflater.inflate(R.layout.exercise_row,null);
        final CheckBox exerciseName = row.findViewById(R.id.exercise_name);
        final Button weightButton = row.findViewById(R.id.weight_button);
        // setup checkbox
        exerciseName.setText(name);
        if(status){
            exerciseName.setChecked(true);
        }
        exerciseName.setOnClickListener(new View.OnClickListener() {
//            boolean checked = exerciseName.isChecked();
            @Override
            public void onClick(View v) {
                if(status){
                    workoutEntity.setStatus(false);
                    workoutViewModel.update(workoutEntity);
                    status = false;
                }
                else{
                    workoutEntity.setStatus(true);
                    workoutViewModel.update(workoutEntity);
                    status = true;
                }
                if(fragment instanceof CurrentWorkoutFragment){
                    ((CurrentWorkoutFragment) fragment).setModified(true);
                    ((CurrentWorkoutFragment) fragment).setPreviouslyModified(true);
                }
            }
        });
        // set up weight button
        if(metricUnits){
            // value in DB is always in murican units
            weight = exerciseEntity.getCurrentWeight()*Variables.KG;
        }
        else{
            weight = exerciseEntity.getCurrentWeight();
        }
        formattedWeight = formatWeight(weight);
        if(weight>=0){
            weightButton.setText(formattedWeight+(metricUnits?" kg":" lb"));
        }
        else{
            weightButton.setText("N/A");
        }
        weightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                final AlertDialog alertDialog = alertDialogBuilder.create();
                View popupView = activity.getLayoutInflater().inflate(R.layout.popup_edit_weight, null);
                alertDialog.setView(popupView);
                alertDialog.setCanceledOnTouchOutside(true);
                alertDialog.show();
                TextView exerciseName = popupView.findViewById(R.id.exercise_name);
                exerciseName.setText(name);
                final EditText weightInput = popupView.findViewById(R.id.weight_input);
                weightInput.setHint(formattedWeight);
                final Switch ignoreWeightSwitch = popupView.findViewById(R.id.ignore_weight_switch);
                if(weight < 0){
                    ignoreWeightSwitch.setChecked(true);
                    ignoreWeight = true;
                    weightInput.setVisibility(View.INVISIBLE);
                }
                ignoreWeightSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        ignoreWeight = isChecked;
                        if(ignoreWeight){
                            weightInput.setVisibility(View.INVISIBLE);
                        }
                        else{
                            weightInput.setHint(Integer.toString(0)); // to get rid of sentinel value from Database
                            weightInput.setVisibility(View.VISIBLE);
                        }
                    }
                });
                Button doneButton = popupView.findViewById(R.id.done_btn);
                doneButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(ignoreWeight){
                            exerciseEntity.setCurrentWeight(Variables.IGNORE_WEIGHT_VALUE);
                            weightButton.setText("N/A");
                            exerciseViewModel.update(exerciseEntity);
                            alertDialog.dismiss();
                        }
                        else if(!weightInput.getText().toString().equals("")){
                            double newWeight = Double.parseDouble(weightInput.getText().toString());
                            weightButton.setText(formatWeight(newWeight)+(metricUnits?" kg":" lb"));
                            if(metricUnits){
                                // convert if in metric
                                newWeight/=Variables.KG;
                            }
                            if(newWeight>exerciseEntity.getMaxWeight()){
                                exerciseEntity.setMaxWeight(newWeight);
                            }
                            else if(newWeight<exerciseEntity.getMinWeight()){
                                exerciseEntity.setMinWeight(newWeight);
                            }
                            exerciseEntity.setCurrentWeight(newWeight);
                            exerciseViewModel.update(exerciseEntity);
                            alertDialog.dismiss();
                        }
                        else{
                            Toast.makeText(activity,"Enter a valid weight!",Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
        // setup video button
        if(videos){
            ImageButton videoButton = row.findViewById(R.id.launch_video);
            videoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(!videoURL.equalsIgnoreCase("none")){
                        // found on SO
                        Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoURL));
                        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoURL));
                        try{
                            context.startActivity(appIntent);
                        }
                        catch(ActivityNotFoundException ex) {
                            context.startActivity(webIntent);
                        }
                    }
                    else{
                        Toast.makeText(activity, "No video found", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
        else{
            ImageView videoButton = row.findViewById(R.id.launch_video);
            videoButton.setVisibility(View.GONE);
        }

        return row;
    }

    private String formatWeight(double aWeight){
        /*
            Formats a weight to either be rounded to 0 decimal points if it's a whole number or 2 if a decimal
         */
        String retVal;
        if ((aWeight == Math.floor(aWeight)) && !Double.isInfinite(aWeight)) {
            // integer type
            retVal = String.format("%.0f", aWeight);
        }
        else{
            retVal = String.format("%.2f", aWeight);
        }
        return retVal;
    }

    public String getFormattedLine(){
            /*
                Utilized whenever writing to a file. This method formats the information of the exercise
                instance into the proper format specified in this project.
             */
        String retVal;
        if(status){
            retVal = name+"*"+Variables.EXERCISE_COMPLETE;
        }
        else{
            retVal = name+"*"+Variables.EXERCISE_INCOMPLETE;
        }
        return retVal;
    }
}
