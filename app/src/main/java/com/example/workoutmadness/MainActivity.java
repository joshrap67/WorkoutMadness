package com.example.workoutmadness;

import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private DrawerLayout drawer;
    private TextView toolbarTitleTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //TODO implement timer??? For in between reps
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbarTitleTV = findViewById(R.id.toolbar_title);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false); // removes the app title from the toolbar
        drawer = findViewById(R.id.drawer);
        NavigationView nav = findViewById(R.id.nav_view);
        nav.setNavigationItemSelectedListener(this);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.nav_draw_open, R.string.nav_draw_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                    new WorkoutFragment()).commit();
            nav.setCheckedItem(R.id.nav_current_workout);
        }
    }

    public void updateToolbarTitle(String aTitle) {
        /*
            Called by other fragments to change the string that the toolbar displays.
         */
        toolbarTitleTV.setText(aTitle);

    }
    public boolean fragModified(){
        Fragment currentFrag = getVisibleFragment();
        if(currentFrag==null){
            return false;
        }
        else if(currentFrag instanceof WorkoutFragment){
            if(((WorkoutFragment) currentFrag).isModified()){
                return true;
            }
        }
        else if(currentFrag instanceof NewWorkoutFragment){
            if(((NewWorkoutFragment) currentFrag).isModified()){
                return true;
            }
        }
        return false;
    }
    @Override
    public void onBackPressed() {
//        FragmentManager fragmentManager = getSupportFragmentManager();
//        Fragment currentFragment = (YourFragmentClass)fragmentManager.findFragmentById(R.id.your_fragment_id);
//        Fragment f = getFragmentManager().findFragmentById(R.id.fragment_container);
        Fragment visibleFragment = getVisibleFragment();
        boolean quit = true;
        // TODO check if new workout is being created, if so ask if user is sure they want to quit
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            // if the user clicked the navigation panel, allow back press to close it.
            drawer.closeDrawer(GravityCompat.START);
            quit = false;
        }
        else if(visibleFragment instanceof WorkoutFragment){
            if(((WorkoutFragment) visibleFragment).isModified()){
                Toast.makeText(this, "Workout was modified", Toast.LENGTH_SHORT).show();
                quit = false;
            }
        }
        else if(visibleFragment instanceof NewWorkoutFragment){
            if(((NewWorkoutFragment) visibleFragment).isModified()){
                Toast.makeText(this, "Creating workout was modified", Toast.LENGTH_SHORT).show();
                quit = false;
            }
        }

        if(quit){
            super.onBackPressed();
        }
    }

    private Fragment getVisibleFragment() {
        /*
            Found on SO
         */
        FragmentManager fragmentManager = MainActivity.this.getSupportFragmentManager();
        List<Fragment> fragments = fragmentManager.getFragments();
        for (Fragment fragment : fragments) {
            if (fragment != null && fragment.isVisible())
                return fragment;
        }
        return null;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        //TODO check if creating new workout, if so ask user are you sure? ask if wanting to save progress
        Fragment currentFrag = getVisibleFragment();
        switch (menuItem.getItemId()) {
            case R.id.nav_current_workout:
                if(currentFrag instanceof NewWorkoutFragment){
                    if(((NewWorkoutFragment) currentFrag).isModified()){
                        Toast.makeText(this, "Creating workout was modified", Toast.LENGTH_SHORT).show();
                    }
                }
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                        new WorkoutFragment(), "CURRENT_WORKOUT").commit();
                break;
            case R.id.nav_new_workout:
                if(currentFrag instanceof WorkoutFragment ){
                    if(((WorkoutFragment) currentFrag).isModified()){
                        Toast.makeText(this, "Workout was modified", Toast.LENGTH_SHORT).show();
                    }
                }
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                        new NewWorkoutFragment(), "NEW_WORKOUT").commit();
                break;
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
