/*
 * Copyright (C) 2014 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.tables.activities;

import java.util.Iterator;
import java.util.List;

import android.app.Fragment;
import android.app.FragmentManager;
import org.opendatakit.IntentConsts;
import org.opendatakit.common.android.activities.BaseActivity;
import org.opendatakit.common.android.application.CommonApplication;
import org.opendatakit.common.android.exception.ServicesAvailabilityException;
import org.opendatakit.common.android.listener.DatabaseConnectionListener;
import org.opendatakit.common.android.utilities.DependencyChecker;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.database.service.OdkDbHandle;
import org.opendatakit.database.service.TableHealthInfo;
import org.opendatakit.database.service.TableHealthStatus;
import org.opendatakit.tables.R;
import org.opendatakit.tables.application.Tables;
import org.opendatakit.tables.utils.Constants;
import org.opendatakit.tables.utils.TableFileUtils;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

/**
 * The base Activity for all ODK Tables activities. Performs basic
 * functionality like retrieving the app name from an intent that all classes
 * should be doing.
 * @author sudar.sam@gmail.com
 *
 */
public abstract class AbsBaseActivity extends BaseActivity {

  protected String mAppName;
  protected String mActionTableId = null;
  
  Bundle mCheckpointTables = new Bundle();
  Bundle mConflictTables = new Bundle();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    this.mAppName = retrieveAppNameFromIntent();
    if ( savedInstanceState != null ) {
      if ( savedInstanceState.containsKey(Constants.IntentKeys.ACTION_TABLE_ID) ) {
        mActionTableId = savedInstanceState.getString(Constants.IntentKeys.ACTION_TABLE_ID);
        if ( mActionTableId != null && mActionTableId.length() == 0 ) {
          mActionTableId = null;
        }
      }
      
      if ( savedInstanceState.containsKey(Constants.IntentKeys.CHECKPOINT_TABLES) ) {
        mCheckpointTables = savedInstanceState.getBundle(Constants.IntentKeys.CHECKPOINT_TABLES);
      }

      if ( savedInstanceState.containsKey(Constants.IntentKeys.CONFLICT_TABLES) ) {
        mConflictTables = savedInstanceState.getBundle(Constants.IntentKeys.CONFLICT_TABLES);
      }
    }

    super.onCreate(savedInstanceState);

    DependencyChecker dc = new DependencyChecker(this);
    boolean dependable = dc.checkDependencies();
    if (!dependable) { // dependencies missing
      return;
    }
  }
  
  
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    
    if ( mActionTableId != null && mActionTableId.length() != 0 ) {
      outState.putString(Constants.IntentKeys.ACTION_TABLE_ID, mActionTableId);
    }
    if ( mCheckpointTables != null && !mCheckpointTables.isEmpty() ) {
      outState.putBundle(Constants.IntentKeys.CHECKPOINT_TABLES, mCheckpointTables);
    }
    if ( mConflictTables != null && !mConflictTables.isEmpty() ) {
      outState.putBundle(Constants.IntentKeys.CONFLICT_TABLES, mConflictTables);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    ((Tables) getApplication()).establishDoNotFireDatabaseConnectionListener(this);
  }

  @Override
  public void onPostResume() {
    super.onPostResume();
    ((Tables) getApplication()).fireDatabaseConnectionListener();
  }

  public String getActionTableId() {
    return mActionTableId;
  }
  
  public void setActionTableId(String tableId) {
    mActionTableId = tableId;
  }
  
  public void scanAllTables() {
    long now = System.currentTimeMillis();
    WebLogger.getLogger(getAppName()).i(this.getClass().getSimpleName(), "scanAllTables -- searching for conflicts and checkpoints ");
    
    CommonApplication app = (CommonApplication) getApplication();
    OdkDbHandle db = null;

    if ( app.getDatabase() == null ) {
      return;
    }
    
    try {
      db = app.getDatabase().openDatabase(mAppName);
      List<TableHealthInfo> tableHealthList = app.getDatabase().getTableHealthStatuses(mAppName, db);
      
      Bundle checkpointTables = new Bundle();
      Bundle conflictTables = new Bundle();
      
      for ( TableHealthInfo tableHealth : tableHealthList ) {
        String tableId = tableHealth.getTableId();
        TableHealthStatus status = tableHealth.getHealthStatus();
        
        if ( status == TableHealthStatus.TABLE_HEALTH_HAS_CHECKPOINTS ||
             status == TableHealthStatus.TABLE_HEALTH_HAS_CHECKPOINTS_AND_CONFLICTS ) {
            checkpointTables.putString(tableId, tableId);
        }
        if ( status == TableHealthStatus.TABLE_HEALTH_HAS_CONFLICTS ||
             status == TableHealthStatus.TABLE_HEALTH_HAS_CHECKPOINTS_AND_CONFLICTS ) {
            conflictTables.putString(tableId, tableId);
        }
      }
      mCheckpointTables = checkpointTables;
      mConflictTables = conflictTables;
    } catch (ServicesAvailabilityException e) {
      WebLogger.getLogger(getAppName()).printStackTrace(e);
    } finally {
      if ( db != null ) {
        try {
          app.getDatabase().closeDatabase(mAppName, db);
        } catch (ServicesAvailabilityException e) {
          WebLogger.getLogger(getAppName()).printStackTrace(e);
          WebLogger.getLogger(getAppName()).e(this.getClass().getSimpleName(),"Unable to close database");
        }
      }
    }
    
    long elapsed = System.currentTimeMillis() - now;
    WebLogger.getLogger(getAppName()).i(this.getClass().getSimpleName(), "scanAllTables -- full table scan completed: " + Long.toString(elapsed) + " ms");
  }
  
  protected void resolveAnyConflicts() {
    // Hijack the app here, after all screens have been resumed,
    // to ensure that all checkpoints and conflicts have been
    // resolved. If they haven't, we branch to the resolution
    // activity.
    
    if ( ( mCheckpointTables == null || mCheckpointTables.isEmpty() ) &&
         ( mConflictTables == null || mConflictTables.isEmpty() ) ) {
      scanAllTables();
    }
    if ( (mCheckpointTables != null) && !mCheckpointTables.isEmpty() ) {
      Iterator<String> iterator = mCheckpointTables.keySet().iterator();
      String tableId = iterator.next();
      mCheckpointTables.remove(tableId);

      Intent i;
      i = new Intent();
      i.setComponent(new ComponentName(IntentConsts.ResolveCheckpoint.APPLICATION_NAME,
          IntentConsts.ResolveCheckpoint.ACTIVITY_NAME));
      i.setAction(Intent.ACTION_EDIT);
      i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, getAppName());
      i.putExtra(IntentConsts.INTENT_KEY_TABLE_ID, tableId);
      try {
        this.startActivityForResult(i, Constants.RequestCodes.LAUNCH_CHECKPOINT_RESOLVER);
      } catch ( ActivityNotFoundException e ) {
        WebLogger.getLogger(mAppName).e(this.getClass().getSimpleName(), "onPostResume: Unable to access ODK Sync");
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
          public void run() {
            AbsBaseActivity.this.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                Toast.makeText(AbsBaseActivity.this, getString(R.string.activity_not_found,
                    IntentConsts.ResolveCheckpoint.ACTIVITY_NAME), Toast.LENGTH_LONG).show();
              }});
          }
        }, 100);
      }
    }
    if ( (mConflictTables != null) && !mConflictTables.isEmpty() ) {
      Iterator<String> iterator = mConflictTables.keySet().iterator();
      String tableId = iterator.next();
      mConflictTables.remove(tableId);

      Intent i;
      i = new Intent();
      i.setComponent(new ComponentName(IntentConsts.ResolveConflict.APPLICATION_NAME,
          IntentConsts.ResolveConflict.ACTIVITY_NAME));
      i.setAction(Intent.ACTION_EDIT);
      i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, getAppName());
      i.putExtra(IntentConsts.INTENT_KEY_TABLE_ID, tableId);
      try {
        this.startActivityForResult(i, Constants.RequestCodes.LAUNCH_CONFLICT_RESOLVER);
      } catch ( ActivityNotFoundException e ) {
        WebLogger.getLogger(mAppName).e(this.getClass().getSimpleName(), "onPostResume: Unable to access ODK Sync");
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
          public void run() {
            AbsBaseActivity.this.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                Toast.makeText(AbsBaseActivity.this, getString(R.string.activity_not_found,
                    IntentConsts.ResolveConflict.ACTIVITY_NAME), Toast.LENGTH_LONG).show();
              }});
          }
        }, 100);
      }
    }
  }

  /**
   * Gets the app name from the Intent. If it is not present it returns a
   * default app name.
   * @return
   */
  String retrieveAppNameFromIntent() {
    String result = 
        this.getIntent().getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
    if (result == null) {
      result = TableFileUtils.getDefaultAppName();
    }
    return result;
  }
  
  /**
   * Get the app name that has been set for this activity.
   * @return
   */
  public String getAppName() {
    return this.mAppName;
  }
  
  /**
   * All Intents in the app expect an app name. This method returns an Intent
   * that can be expected to play nice with other activities. The class is not
   * set.
   * @return
   */
  public Intent createNewIntentWithAppName() {
    Intent intent = new Intent();
    intent.putExtra(IntentConsts.INTENT_KEY_APP_NAME, getAppName());
    return intent;
  }

  @Override
  public void databaseAvailable() {
    if ( getAppName() != null ) {
      resolveAnyConflicts();
    }
    FragmentManager mgr = this.getFragmentManager();
    int idxLast = mgr.getBackStackEntryCount() - 1;
    if (idxLast >= 0) {
      FragmentManager.BackStackEntry entry = mgr.getBackStackEntryAt(idxLast);
      Fragment newFragment = null;
      newFragment = mgr.findFragmentByTag(entry.getName());
      if ( newFragment instanceof DatabaseConnectionListener) {
        ((DatabaseConnectionListener) newFragment).databaseAvailable();
      }
    }
  }

  @Override
  public void databaseUnavailable() {
    FragmentManager mgr = this.getFragmentManager();
    int idxLast = mgr.getBackStackEntryCount() - 1;
    if (idxLast >= 0) {
      FragmentManager.BackStackEntry entry = mgr.getBackStackEntryAt(idxLast);
      Fragment newFragment = null;
      newFragment = mgr.findFragmentByTag(entry.getName());
      if ( newFragment instanceof DatabaseConnectionListener ) {
        ((DatabaseConnectionListener) newFragment).databaseUnavailable();
      }
    }
  }

}
