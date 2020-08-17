package com.joshrap.liteweight.network.repos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshrap.liteweight.models.ResultStatus;
import com.joshrap.liteweight.models.Routine;
import com.joshrap.liteweight.models.User;
import com.joshrap.liteweight.models.UserWithWorkout;
import com.joshrap.liteweight.models.Workout;
import com.joshrap.liteweight.network.ApiGateway;
import com.joshrap.liteweight.network.RequestFields;

import java.util.HashMap;
import java.util.Map;

import lombok.NonNull;

public class WorkoutRepository {

    private static final String newWorkoutAction = "newWorkout";
    private static final String switchWorkoutAction = "switchWorkout";
    private static final String copyWorkoutAction = "copyWorkout";
    private static final String renameWorkoutAction = "renameWorkout";
    private static final String deleteWorkoutAction = "deleteWorkout";
    private static final String resetWorkoutStatisticsAction = "resetWorkoutStatistics";
    private static final String editWorkoutAction = "editWorkout";

    public static ResultStatus<UserWithWorkout> createWorkout(@NonNull Routine routine, @NonNull String workoutName) {
        ResultStatus<UserWithWorkout> resultStatus = new ResultStatus<>();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Workout.ROUTINE, routine.asMap());
        requestBody.put(Workout.WORKOUT_NAME, workoutName);

        ResultStatus<String> apiResponse = ApiGateway.makeRequest(newWorkoutAction, requestBody, true);

        if (apiResponse.isSuccess()) {
            try {
                resultStatus.setData(new UserWithWorkout(new ObjectMapper().readValue(apiResponse.getData(), Map.class)));
                resultStatus.setSuccess(true);
            } catch (Exception e) {
                resultStatus.setErrorMessage("Unable to create workout. 2");
            }
        } else if (apiResponse.isNetworkError()) {
            resultStatus.setErrorMessage("Network error. Unable to create workout. Check internet connection.");
        } else {
            resultStatus.setErrorMessage("Unable to create workout. 3");
        }
        return resultStatus;
    }

    public static ResultStatus<UserWithWorkout> copyWorkout(@NonNull Workout workout, @NonNull String workoutName) {
        ResultStatus<UserWithWorkout> resultStatus = new ResultStatus<>();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(RequestFields.WORKOUT, workout.asMap());
        requestBody.put(Workout.WORKOUT_NAME, workoutName);

        ResultStatus<String> apiResponse = ApiGateway.makeRequest(copyWorkoutAction, requestBody, true);

        if (apiResponse.isSuccess()) {
            try {
                resultStatus.setData(new UserWithWorkout(new ObjectMapper().readValue(apiResponse.getData(), Map.class)));
                resultStatus.setSuccess(true);
            } catch (Exception e) {
                resultStatus.setErrorMessage("Unable to copy workout. 2");
            }
        } else if (apiResponse.isNetworkError()) {
            resultStatus.setErrorMessage("Network error. Unable to copy workout. Check internet connection.");
        } else {
            resultStatus.setErrorMessage("Unable to copy workout. 3");
        }
        return resultStatus;
    }

    public static ResultStatus<UserWithWorkout> switchWorkout(@NonNull Workout oldWorkout, @NonNull String workoutId) {
        ResultStatus<UserWithWorkout> resultStatus = new ResultStatus<>();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(RequestFields.WORKOUT, oldWorkout.asMap());
        requestBody.put(Workout.WORKOUT_ID, workoutId);

        ResultStatus<String> apiResponse = ApiGateway.makeRequest(switchWorkoutAction, requestBody, true);

        if (apiResponse.isSuccess()) {
            try {
                resultStatus.setData(new UserWithWorkout(new ObjectMapper().readValue(apiResponse.getData(), Map.class)));
                resultStatus.setSuccess(true);
            } catch (Exception e) {
                resultStatus.setErrorMessage("Unable to switch workout. 2");
            }
        } else if (apiResponse.isNetworkError()) {
            resultStatus.setErrorMessage("Network error. Unable to switch workout. Check internet connection.");
        } else {
            resultStatus.setErrorMessage("Unable to switch workout. 3");
        }
        return resultStatus;
    }

    public static ResultStatus<User> renameWorkout(@NonNull String workoutId, @NonNull String workoutName) {
        ResultStatus<User> resultStatus = new ResultStatus<>();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Workout.WORKOUT_ID, workoutId);
        requestBody.put(Workout.WORKOUT_NAME, workoutName);

        ResultStatus<String> apiResponse = ApiGateway.makeRequest(renameWorkoutAction, requestBody, true);

        if (apiResponse.isSuccess()) {
            try {
                resultStatus.setData(new User(new ObjectMapper().readValue(apiResponse.getData(), Map.class)));
                resultStatus.setSuccess(true);
            } catch (Exception e) {
                resultStatus.setErrorMessage("Unable to copy workout. 2");
            }
        } else if (apiResponse.isNetworkError()) {
            resultStatus.setErrorMessage("Network error. Unable to copy workout. Check internet connection.");
        } else {
            resultStatus.setErrorMessage("Unable to copy workout. 3");
        }
        return resultStatus;
    }

    public static ResultStatus<UserWithWorkout> deleteWorkout(@NonNull String workoutId) {
        ResultStatus<UserWithWorkout> resultStatus = new ResultStatus<>();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Workout.WORKOUT_ID, workoutId);

        ResultStatus<String> apiResponse = ApiGateway.makeRequest(deleteWorkoutAction, requestBody, true);

        if (apiResponse.isSuccess()) {
            try {
                resultStatus.setData(new UserWithWorkout(new ObjectMapper().readValue(apiResponse.getData(), Map.class)));
                resultStatus.setSuccess(true);
            } catch (Exception e) {
                resultStatus.setErrorMessage("Unable to copy workout. 2");
            }
        } else if (apiResponse.isNetworkError()) {
            resultStatus.setErrorMessage("Network error. Unable to copy workout. Check internet connection.");
        } else {
            resultStatus.setErrorMessage("Unable to copy workout. 3");
        }
        return resultStatus;
    }

    public static ResultStatus<User> resetWorkoutStatistics(@NonNull String workoutId) {
        ResultStatus<User> resultStatus = new ResultStatus<>();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Workout.WORKOUT_ID, workoutId);

        ResultStatus<String> apiResponse = ApiGateway.makeRequest(resetWorkoutStatisticsAction, requestBody, true);

        if (apiResponse.isSuccess()) {
            try {
                resultStatus.setData(new User(new ObjectMapper().readValue(apiResponse.getData(), Map.class)));
                resultStatus.setSuccess(true);
            } catch (Exception e) {
                resultStatus.setErrorMessage("Unable to copy workout. 2");
            }
        } else if (apiResponse.isNetworkError()) {
            resultStatus.setErrorMessage("Network error. Unable to copy workout. Check internet connection.");
        } else {
            resultStatus.setErrorMessage("Unable to copy workout. 3");
        }
        return resultStatus;
    }

    public static ResultStatus<UserWithWorkout> editWorkout(@NonNull String workoutId, @NonNull Workout workout) {
        ResultStatus<UserWithWorkout> resultStatus = new ResultStatus<>();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Workout.WORKOUT_ID, workoutId);
        requestBody.put(RequestFields.WORKOUT, workout.asMap());

        ResultStatus<String> apiResponse = ApiGateway.makeRequest(editWorkoutAction, requestBody, true);

        if (apiResponse.isSuccess()) {
            try {
                resultStatus.setData(new UserWithWorkout(new ObjectMapper().readValue(apiResponse.getData(), Map.class)));
                resultStatus.setSuccess(true);
            } catch (Exception e) {
                resultStatus.setErrorMessage("Unable to edit workout. 2");
            }
        } else if (apiResponse.isNetworkError()) {
            resultStatus.setErrorMessage("Network error. Unable to edit workout. Check internet connection.");
        } else {
            resultStatus.setErrorMessage("Unable to edit workout. 3");
        }
        return resultStatus;
    }
}
