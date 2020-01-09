package com.joshrap.liteweight.Globals;

public class Variables {
    /*
        Contains static variables
     */
    public static final int
            NAME_INDEX = 0,
            VIDEO_INDEX = 1,
            FOCUS_INDEX_FILE = 2,
            IGNORE_WEIGHT_VALUE = -1,
            MAX_NUMBER_OF_WORKOUTS = 50,
            MAX_NUMBER_OF_CUSTOM_EXERCISES = 200;

    public static final double KG = 0.45359237;

    public static final String
            DEFAULT_EXERCISES_FILE = "DefaultExercises.txt",
            SPLIT_DELIM = "\\*",
            FOCUS_DELIM_DB = ",",
            SHARED_PREF_NAME = "userSettings",
            VIDEO_KEY = "Videos",
            STOPWATCH_KEY = "Stopwatch",
            DATABASE_NAME = "workout_db",
            DB_EMPTY_KEY = "DB_EMPTY",
            UNIT_KEY = "METRIC",
            DATE_PATTERN = "MM/dd/yyyy HH:mm:ss",
            ABOUT_TITLE = "About",
            CURRENT_WORKOUT_TITLE = "Current Workout",
            MY_EXERCISES_TITLE = "My Exercises",
            MY_WORKOUT_TITLE = "My Workouts",
            NEW_WORKOUT_TITLE = "Workout Creator",
            SETTINGS_TITLE = "Settings",
            QUIT_TITLE = "Quit";
}
