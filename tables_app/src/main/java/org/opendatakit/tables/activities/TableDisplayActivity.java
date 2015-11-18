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

import org.opendatakit.common.android.data.TableViewType;
import org.opendatakit.common.android.data.UserTable;
import org.opendatakit.common.android.utilities.TableUtil;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.database.service.OdkDbHandle;
import org.opendatakit.tables.R;
import org.opendatakit.tables.application.Tables;
import org.opendatakit.tables.data.PossibleTableViewTypes;
import org.opendatakit.tables.fragments.DetailViewFragment;
import org.opendatakit.tables.fragments.ListViewFragment;
import org.opendatakit.tables.fragments.MapListViewFragment;
import org.opendatakit.tables.fragments.SpreadsheetFragment;
import org.opendatakit.tables.fragments.TableMapInnerFragment;
import org.opendatakit.tables.fragments.TableMapInnerFragment.TableMapInnerFragmentListener;
import org.opendatakit.tables.utils.ActivityUtil;
import org.opendatakit.tables.utils.CollectUtil;
import org.opendatakit.tables.utils.Constants;
import org.opendatakit.tables.utils.IntentUtil;
import org.opendatakit.tables.utils.SQLQueryStruct;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

/**
 * Displays information about a table. List, Map, and Detail views are all
 * displayed via this activity.
 * 
 * @author sudar.sam@gmail.com
 *
 */
public class TableDisplayActivity extends AbsTableActivity implements TableMapInnerFragmentListener {

  private static final String TAG = TableDisplayActivity.class.getSimpleName();
  public static final String INTENT_KEY_CURRENT_FRAGMENT = "saveInstanceCurrentFragment";

  /**
   * The fragment types this activity could be displaying.
   * 
   * @author sudar.sam@gmail.com
   *
   */
  public enum ViewFragmentType {
    SPREADSHEET, LIST, MAP, DETAIL;
  }
  /**
   * The type of fragment that is currently being displayed.
   */
  private ViewFragmentType mCurrentFragmentType;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.mCurrentFragmentType = this.retrieveFragmentTypeToDisplay(savedInstanceState);
    this.setContentView(R.layout.activity_table_display_activity);
  }

  @Override
  public void onPostResume() {
    super.onPostResume();
    Tables.getInstance().establishDatabaseConnectionListener(this);
  }

  private boolean reloadCachedValues = false;
  
  /** Cached data from database */
  private PossibleTableViewTypes mPossibleTableViewTypes;

  /**
   * The {@link UserTable} that is being displayed in this activity.
   */
  private UserTable mUserTable;

  @Override
  public void databaseAvailable() {
    if ( Tables.getInstance().getDatabase() != null ) {
      // see if we saved the state
      OdkDbHandle db = null;
      try {
        db = Tables.getInstance().getDatabase().openDatabase(getAppName());
        if (mCurrentFragmentType == null) {
          // if we don't have a view set, use the default from the database
          TableViewType type;
          type = TableUtil.get().getDefaultViewType(Tables.getInstance(), getAppName(), db, getTableId());
          mCurrentFragmentType = this.getViewFragmentTypeFromViewType(type);
          if (mCurrentFragmentType == null) {
            // and if that isn't set, use spreadsheet
            WebLogger.getLogger(getAppName()).i(TAG,
                "[retrieveFragmentTypeToDisplay] no view type found, " + "defaulting to spreadsheet");
            mCurrentFragmentType = ViewFragmentType.SPREADSHEET;
          }
        }
        if ( reloadCachedValues || mPossibleTableViewTypes == null ) {
          this.mPossibleTableViewTypes = this.getPossibleTableViewTypes(db);
        }
        if ( reloadCachedValues || mUserTable == null ) {
          this.initializeBackingTable();
        }
        reloadCachedValues = false;
      } catch (RemoteException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        WebLogger.getLogger(getAppName()).e(TAG,
            "[databaseAvailable] unable to access database");
        Toast.makeText(this, "Unable to access database", Toast.LENGTH_LONG).show();
      } finally {
        if (db != null) {
          try {
            Tables.getInstance().getDatabase().closeDatabase(getAppName(), db);
          } catch (RemoteException e) {
            WebLogger.getLogger(getAppName()).printStackTrace(e);
            WebLogger.getLogger(getAppName()).e(TAG,
                "[databaseAvailable] unable to access database");
            Toast.makeText(this, "Unable to access database", Toast.LENGTH_LONG).show();
          }
        }
      }
      this.initializeDisplayFragment();
      Handler handler = new Handler() {};
      handler.postDelayed(new Runnable() {

        @Override
        public void run() {
          notifyCurrentFragment(true);
        }}, 100);
    }
  }

  @Override
  public void databaseUnavailable() {
    notifyCurrentFragment(false);
  }
  
  private void notifyCurrentFragment(boolean databaseAvailable) {
    FragmentManager fragmentManager = this.getFragmentManager();
    
    switch ( mCurrentFragmentType ) {
    case SPREADSHEET:
      SpreadsheetFragment spreadsheetFragment = (SpreadsheetFragment) fragmentManager
        .findFragmentByTag(mCurrentFragmentType.name());
      if ( spreadsheetFragment != null ) {
        if ( databaseAvailable ) {
          spreadsheetFragment.databaseAvailable();
        } else {
          spreadsheetFragment.databaseUnavailable();
        }
      }
      break;
    case LIST:
      ListViewFragment listViewFragment = (ListViewFragment) fragmentManager
      .findFragmentByTag(mCurrentFragmentType.name());
      if ( listViewFragment != null ) {
        if ( databaseAvailable ) {
          listViewFragment.databaseAvailable();
        } else {
          listViewFragment.databaseUnavailable();
        }
      }
      break;
    case MAP:
      MapListViewFragment mapListViewFragment = (MapListViewFragment) fragmentManager
        .findFragmentByTag(mCurrentFragmentType.name());
      if ( mapListViewFragment != null ) {
        if ( databaseAvailable ) {
          mapListViewFragment.databaseAvailable();
        } else {
          mapListViewFragment.databaseUnavailable();
        }
      }
      break;
    case DETAIL:
      DetailViewFragment detailViewFragment = (DetailViewFragment) fragmentManager
      .findFragmentByTag(mCurrentFragmentType.name());
      if ( detailViewFragment != null ) {
        if ( databaseAvailable ) {
          detailViewFragment.databaseAvailable();
        } else {
          detailViewFragment.databaseUnavailable();
        }
      }
      break;
    }

  }
  
  public static String getFragmentTag(ViewFragmentType fragmentType) {
    return fragmentType.name();
  }
  
  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    this.mCurrentFragmentType = this.retrieveFragmentTypeToDisplay(savedInstanceState);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    WebLogger.getLogger(getAppName()).d(TAG, "[onDestroy]");
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (this.mCurrentFragmentType != null) {
      WebLogger.getLogger(getAppName())
          .i(TAG,
              "[onSaveInstanceState] saving current fragment type: "
                  + this.mCurrentFragmentType.name());
      outState.putString(INTENT_KEY_CURRENT_FRAGMENT, this.mCurrentFragmentType.name());
    } else {
      WebLogger.getLogger(getAppName()).i(TAG,
          "[onSaveInstanceState] no current fragment type to save");
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    WebLogger.getLogger(getAppName()).i(TAG, "[onResume]");
  }
  
  private PossibleTableViewTypes getPossibleTableViewTypes(OdkDbHandle db) throws RemoteException {
    PossibleTableViewTypes viewTypes = null;
    viewTypes = new PossibleTableViewTypes(getAppName(), db, getTableId(), getColumnDefinitions());
    return viewTypes;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // clear the menu so that we don't double inflate
    menu.clear();
    MenuInflater menuInflater = this.getMenuInflater();
    switch (this.getCurrentFragmentType()) {
    case SPREADSHEET:
    case LIST:
    case MAP:
      menuInflater.inflate(R.menu.top_level_table_menu, menu);
      this.enableAndDisableViewTypes(mPossibleTableViewTypes, menu);
      this.selectCorrectViewType(menu);
      break;
    case DETAIL:
      menuInflater.inflate(R.menu.detail_view_menu, menu);
      break;
    }
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.top_level_table_menu_view_spreadsheet_view:
      this.showSpreadsheetFragment();
      return true;
    case R.id.top_level_table_menu_view_list_view:
      try {
        this.showListFragment();
      } catch (RemoteException e1) {
        WebLogger.getLogger(getAppName()).printStackTrace(e1);
        Toast.makeText(this, "Unable to access database", Toast.LENGTH_LONG).show();
      }
      return true;
    case R.id.top_level_table_menu_view_map_view:
      try {
        this.showMapFragment();
      } catch (RemoteException e1) {
        WebLogger.getLogger(getAppName()).printStackTrace(e1);
        Toast.makeText(this, "Unable to access database", Toast.LENGTH_LONG).show();
      }
      return true;
    case R.id.top_level_table_menu_add:
      WebLogger.getLogger(getAppName()).d(TAG, "[onOptionsItemSelected] add selected");
      try {
        ActivityUtil.addRow(this, this.getAppName(), this.getTableId(), this.getColumnDefinitions(),
            null);
      } catch (RemoteException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        Toast.makeText(this, "Unable to access database", Toast.LENGTH_LONG).show();
      }
      return true;
    case R.id.top_level_table_menu_table_properties:
      ActivityUtil.launchTableLevelPreferencesActivity(this, this.getAppName(), this.getTableId(),
          TableLevelPreferencesActivity.FragmentType.TABLE_PREFERENCE);
      return true;
    case R.id.menu_edit_row:
      // We need to retrieve the row id.
      DetailViewFragment detailViewFragment = this.findDetailViewFragment();
      if (detailViewFragment == null) {
        WebLogger.getLogger(getAppName()).e(TAG,
            "[onOptionsItemSelected] trying to edit row, but detail view " + " fragment null");
        Toast.makeText(this, getString(R.string.cannot_edit_row_please_try_again),
            Toast.LENGTH_LONG).show();
        return true;
      }
      String rowId = detailViewFragment.getRowId();
      if (rowId == null) {
        WebLogger.getLogger(getAppName()).e(TAG,
            "[onOptionsItemSelected trying to edit row, but row id is null");
        Toast.makeText(this, getString(R.string.cannot_edit_row_please_try_again),
            Toast.LENGTH_LONG).show();
        return true;
      }
      try {
        ActivityUtil.editRow(this, this.getAppName(), this.getTableId(), this.getColumnDefinitions(),
          rowId);
      } catch (RemoteException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        Toast.makeText(this, "Unable to access database", Toast.LENGTH_LONG).show();
      }
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    try {
      switch (requestCode) {
      case Constants.RequestCodes.LAUNCH_CHECKPOINT_RESOLVER:
      case Constants.RequestCodes.LAUNCH_CONFLICT_RESOLVER:
        // these are no-ops on return, as the onResume() method will deal with
        // any fall-out from them.
        this.refreshDataTable();
        this.refreshDisplayFragment();
        break;
      // For now, we will just refresh the table if something could have changed.
      case Constants.RequestCodes.ADD_ROW_COLLECT:
        if (resultCode == Activity.RESULT_OK) {
          WebLogger.getLogger(getAppName()).d(TAG,
              "[onActivityResult] result ok, refreshing backing table");
          CollectUtil.handleOdkCollectAddReturn(getBaseContext(), getAppName(), getTableId(),
              resultCode, data);
  
          this.refreshDataTable();
          // We also want to cause the fragments to redraw themselves, as their
          // data may have changed.
          this.refreshDisplayFragment();
        } else {
          WebLogger.getLogger(getAppName()).d(TAG,
              "[onActivityResult] result canceled, not refreshing backing " + "table");
        }
        break;
      case Constants.RequestCodes.EDIT_ROW_COLLECT:
        if (resultCode == Activity.RESULT_OK) {
          WebLogger.getLogger(getAppName()).d(TAG,
              "[onActivityResult] result ok, refreshing backing table");
          CollectUtil.handleOdkCollectEditReturn(getBaseContext(), getAppName(), getTableId(),
              resultCode, data);
  
          this.refreshDataTable();
          // We also want to cause the fragments to redraw themselves, as their
          // data may have changed.
          this.refreshDisplayFragment();
        } else {
          WebLogger.getLogger(getAppName()).d(TAG,
              "[onActivityResult] result canceled, not refreshing backing " + "table");
        }
        break;
      case Constants.RequestCodes.ADD_ROW_SURVEY:
      case Constants.RequestCodes.EDIT_ROW_SURVEY:
        if (resultCode == Activity.RESULT_OK) {
          WebLogger.getLogger(getAppName()).d(TAG,
              "[onActivityResult] result ok, refreshing backing table");
        } else {
          WebLogger.getLogger(getAppName()).d(TAG,
              "[onActivityResult] result canceled, refreshing backing table");
        }
        // verify that the data table doesn't contain checkpoints...
        // always refresh, as survey may have done something
        this.refreshDataTable();
        this.refreshDisplayFragment();
        break;
      }
    } catch ( RemoteException e ) {
      WebLogger.getLogger(getAppName()).printStackTrace(e);
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  /**
   * Disable or enable those menu items corresponding to view types that are
   * currently invalid or valid, respectively. The inflatedMenu must have
   * already been created from the resource.
   * 
   * @param possibleViews
   * @param inflatedMenu
   */
  private void enableAndDisableViewTypes(PossibleTableViewTypes possibleViews, Menu inflatedMenu) {
    MenuItem spreadsheetItem = inflatedMenu
        .findItem(R.id.top_level_table_menu_view_spreadsheet_view);
    MenuItem listItem = inflatedMenu.findItem(R.id.top_level_table_menu_view_list_view);
    MenuItem mapItem = inflatedMenu.findItem(R.id.top_level_table_menu_view_map_view);
    spreadsheetItem.setEnabled((possibleViews != null) && possibleViews.spreadsheetViewIsPossible());
    listItem.setEnabled((possibleViews != null) && possibleViews.listViewIsPossible());
    mapItem.setEnabled((possibleViews != null) && possibleViews.mapViewIsPossible());
  }

  /**
   * Selects the correct view type that is being displayed by the
   * {@see ITopLevelTableMenuActivity}.
   * 
   * @param inflatedMenu
   */
  private void selectCorrectViewType(Menu inflatedMenu) {
    ViewFragmentType currentFragment = this.getCurrentFragmentType();
    if (currentFragment == null) {
      WebLogger.getLogger(getAppName()).e(TAG,
          "did not find a current fragment type. Not selecting view.");
      return;
    }
    MenuItem menuItem = null;
    switch (currentFragment) {
    case SPREADSHEET:
      menuItem = inflatedMenu.findItem(R.id.top_level_table_menu_view_spreadsheet_view);
      menuItem.setChecked(true);
      break;
    case LIST:
      menuItem = inflatedMenu.findItem(R.id.top_level_table_menu_view_list_view);
      menuItem.setChecked(true);
      break;
    case MAP:
      menuItem = inflatedMenu.findItem(R.id.top_level_table_menu_view_map_view);
      menuItem.setChecked(true);
      break;
    default:
      WebLogger.getLogger(getAppName()).e(TAG, "view type not recognized: " + currentFragment);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    WebLogger.getLogger(getAppName()).i(TAG, "[onStart]");
  }

  public void refreshDisplayFragment() {
    WebLogger.getLogger(getAppName()).d(TAG, "[refreshDisplayFragment]");
    this.helperInitializeDisplayFragment(true);
  }

  protected void initializeDisplayFragment() {
    this.helperInitializeDisplayFragment(false);
  }

  /**
   * Initialize the correct display fragment based on the result of
   * {@link #retrieveTableIdFromIntent()}. Initializes Spreadsheet if none is
   * present in Intent.
   */
  private void helperInitializeDisplayFragment(boolean createNew) {
    try {
      switch (this.mCurrentFragmentType) {
      case SPREADSHEET:
        this.showSpreadsheetFragment(createNew);
        break;
      case DETAIL:
        this.showDetailFragment(createNew);
        break;
      case LIST:
        this.showListFragment(createNew);
        break;
      case MAP:
        this.showMapFragment(createNew);
        break;
      default:
        WebLogger.getLogger(getAppName()).e(TAG,
            "ViewFragmentType not recognized: " + this.mCurrentFragmentType);
        break;
      }
    } catch (RemoteException e) {
      WebLogger.getLogger(getAppName()).printStackTrace(e);
      Toast.makeText(this, "Unable to access database", Toast.LENGTH_LONG).show();
    }
  }

  /**
   * Set the current type of fragment that is being displayed.
   * Called when mocking interface.
   * 
   * @param currentType
   */
  public void setCurrentFragmentType(ViewFragmentType currentType) {
    this.mCurrentFragmentType = currentType;
    this.invalidateOptionsMenu();
  }

  /**
   * @return the {@link ViewFragmentType} that was passed in the intent, or null
   *         if none exists.
   */
  protected ViewFragmentType retrieveViewFragmentTypeFromIntent() {
    if (this.getIntent().getExtras() == null) {
      return null;
    }
    String viewFragmentTypeStr = this.getIntent().getExtras()
        .getString(Constants.IntentKeys.TABLE_DISPLAY_VIEW_TYPE);
    if (viewFragmentTypeStr == null) {
      return null;
    } else {
      ViewFragmentType result = ViewFragmentType.valueOf(viewFragmentTypeStr);
      return result;
    }
  }

  /**
   * Get the {@link ViewFragmentType} that should be displayed. Any type in the
   * passed in bundle takes precedence, on the assumption that is was from a
   * saved instance state. Next is any type that was passed in the Intent. If
   * neither is present, the value corresponding to
   * {@see TableUtil#getDefaultViewType()} wins. If none is present, returns
   * {@link ViewFragmentType#SPREADSHEET}.
   * 
   * @return
   */
  protected ViewFragmentType retrieveFragmentTypeToDisplay(Bundle savedInstanceState) {
    // 1) First check the passed in bundle.
    if (savedInstanceState != null && savedInstanceState.containsKey(INTENT_KEY_CURRENT_FRAGMENT)) {
      String instanceTypeStr = savedInstanceState.getString(INTENT_KEY_CURRENT_FRAGMENT);
      WebLogger.getLogger(getAppName()).i(
          TAG,
          "[retrieveFragmentTypeToDisplay] found type in saved instance" + " state: "
              + instanceTypeStr);
      return ViewFragmentType.valueOf(instanceTypeStr);
    }
    WebLogger.getLogger(getAppName()).i(TAG,
        "[retrieveFragmentTypeToDisplay] didn't find fragment type " + "in saved instance state");
    // 2) then check the intent
    ViewFragmentType result = retrieveViewFragmentTypeFromIntent();
    return result;
  }

  /**
   * Get the {@link ViewFragmentType} that corresponds to {@link TableViewType}.
   * If no match is found, returns null.
   * 
   * @param viewType
   * @return
   */
  public ViewFragmentType getViewFragmentTypeFromViewType(TableViewType viewType) {
    switch (viewType) {
    case SPREADSHEET:
      return ViewFragmentType.SPREADSHEET;
    case MAP:
      return ViewFragmentType.MAP;
    case LIST:
      return ViewFragmentType.LIST;
    default:
      WebLogger.getLogger(getAppName()).e(TAG, "viewType " + viewType + " not recognized.");
      return null;
    }
  }

  /**
   * Get the {@link UserTable} that is being held by this activity.
   * 
   * @return
   */
  public UserTable getUserTable() {
    if ( mUserTable == null ) {
      try {
        initializeBackingTable();
      } catch (RemoteException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return null;
      }
    }
    return this.mUserTable;
  }

  /**
   * Refresh the data being displayed.
   * @throws RemoteException 
   */
  public void refreshDataTable() throws RemoteException {
    this.initializeBackingTable();
  }

  /**
   * Get the {@link UserTable} from the database that should be displayed.
   * 
   * @return
   * @throws RemoteException 
   */
  void initializeBackingTable() throws RemoteException {
    SQLQueryStruct sqlQueryStruct = this.retrieveSQLQueryStatStructFromIntent();
    OdkDbHandle db = null;
    String[] emptyArray = {};
    try {
      db = Tables.getInstance().getDatabase().openDatabase(getAppName());
      UserTable result = Tables.getInstance().getDatabase().rawSqlQuery(this.getAppName(), db,
          this.getTableId(), getColumnDefinitions(), sqlQueryStruct.whereClause,
          (sqlQueryStruct.selectionArgs == null) ? emptyArray : sqlQueryStruct.selectionArgs, 
          (sqlQueryStruct.groupBy == null) ? emptyArray : sqlQueryStruct.groupBy, 
          sqlQueryStruct.having, sqlQueryStruct.orderByElementKey, sqlQueryStruct.orderByDirection);
      mUserTable = result;
    } finally {
      if (db != null) {
        Tables.getInstance().getDatabase().closeDatabase(getAppName(), db);
      }
    }
  }

  /**
   * Retrieve the {@link SQLQueryStruct} specified in the {@link Intent} that
   * restricts the current table.
   * 
   * @return
   */
  SQLQueryStruct retrieveSQLQueryStatStructFromIntent() {
    SQLQueryStruct result = IntentUtil.getSQLQueryStructFromBundle(this.getIntent().getExtras());
    return result;
  }

  /**
   * Show the spreadsheet fragment, creating a new one if it doesn't yet exist.
   */
  public void showSpreadsheetFragment() {
    this.showSpreadsheetFragment(false);
  }

  /**
   * Show the spreadsheet fragment.
   * 
   * @param createNew
   */
  public void showSpreadsheetFragment(boolean createNew) {
    this.setCurrentFragmentType(ViewFragmentType.SPREADSHEET);
    this.updateChildViewVisibility(ViewFragmentType.SPREADSHEET);
    FragmentManager fragmentManager = this.getFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
    // Hide all the other fragments.
    this.hideAllOtherViewFragments(ViewFragmentType.SPREADSHEET, fragmentTransaction);
    // Try to retrieve one already there.
    SpreadsheetFragment spreadsheetFragment = (SpreadsheetFragment) fragmentManager
        .findFragmentByTag(ViewFragmentType.SPREADSHEET.name());
    if (spreadsheetFragment == null) {
      WebLogger.getLogger(getAppName()).d(TAG,
          "[showSpreadsheetFragment] no existing spreadshseet " + "fragment found");
    } else {
      WebLogger.getLogger(getAppName()).d(TAG,
              "[showSpreadsheetFragment] existing spreadsheet fragment " + "found");
    }
    WebLogger.getLogger(getAppName())
        .d(TAG, "[showSpreadsheetFragment] createNew is: " + createNew);
    if (spreadsheetFragment == null || createNew) {
      if (spreadsheetFragment != null) {
        WebLogger.getLogger(getAppName()).d(TAG,
            "[showSpreadsheetFragment] removing existing fragment");
        // Get rid of the existing fragment
        fragmentTransaction.remove(spreadsheetFragment);
      }
      spreadsheetFragment = this.createSpreadsheetFragment();
      fragmentTransaction.add(R.id.activity_table_display_activity_one_pane_content,
          spreadsheetFragment, ViewFragmentType.SPREADSHEET.name());
    } else {
      fragmentTransaction.show(spreadsheetFragment);
    }
    fragmentTransaction.commit();
  }

  /**
   * Hide every fragment except that specified by fragmentToKeepVisible.
   * 
   * @param fragmentToKeepVisible
   * @param fragmentTransaction
   *          the transaction on which the calls to hide the fragments is to be
   *          performed
   */
  private void hideAllOtherViewFragments(ViewFragmentType fragmentToKeepVisible,
      FragmentTransaction fragmentTransaction) {
    FragmentManager fragmentManager = this.getFragmentManager();
    // First acquire all the possible fragments.
    Fragment spreadsheet = fragmentManager.findFragmentByTag(ViewFragmentType.SPREADSHEET.name());
    Fragment list = fragmentManager.findFragmentByTag(ViewFragmentType.LIST.name());
    Fragment mapList = fragmentManager.findFragmentByTag(Constants.FragmentTags.MAP_LIST);
    Fragment mapInner = fragmentManager.findFragmentByTag(Constants.FragmentTags.MAP_INNER_MAP);
    Fragment detailFragment = fragmentManager
        .findFragmentByTag(ViewFragmentType.DETAIL.name());
    if (fragmentToKeepVisible != ViewFragmentType.SPREADSHEET && spreadsheet != null) {
      fragmentTransaction.hide(spreadsheet);
    }
    if (fragmentToKeepVisible != ViewFragmentType.LIST && list != null) {
      fragmentTransaction.hide(list);
    }
    if (fragmentToKeepVisible != ViewFragmentType.DETAIL && detailFragment != null) {
      fragmentTransaction.hide(detailFragment);
    }
    if (fragmentToKeepVisible != ViewFragmentType.MAP) {
      if (mapList != null) {
        fragmentTransaction.hide(mapList);
      }
      if (mapInner != null) {
        fragmentTransaction.hide(mapInner);
      }
    }
  }

  /**
   * Create a {@link SpreadsheetFragment} to be displayed in the activity.
   * 
   * @return
   */
  SpreadsheetFragment createSpreadsheetFragment() {
    SpreadsheetFragment result = new SpreadsheetFragment();
    return result;
  }

  public void showMapFragment() throws RemoteException {
    this.showMapFragment(false);
  }

  public void showMapFragment(boolean createNew) throws RemoteException {
    this.setCurrentFragmentType(ViewFragmentType.MAP);
    this.updateChildViewVisibility(ViewFragmentType.MAP);
    // Set the list view file name.
    String fileName = IntentUtil.retrieveFileNameFromBundle(this.getIntent().getExtras());
    if (fileName == null) {
      OdkDbHandle db = null;
      try {
        db = Tables.getInstance().getDatabase().openDatabase(getAppName());
        fileName = TableUtil.get().getMapListViewFilename(Tables.getInstance(), getAppName(), db, getTableId());
      } finally {
        if (db != null) {
          Tables.getInstance().getDatabase().closeDatabase(getAppName(), db);
        }
      }
    }
    FragmentManager fragmentManager = this.getFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
    this.hideAllOtherViewFragments(ViewFragmentType.MAP, fragmentTransaction);
    MapListViewFragment mapListViewFragment = (MapListViewFragment) fragmentManager
        .findFragmentByTag(Constants.FragmentTags.MAP_LIST);
    TableMapInnerFragment innerMapFragment = (TableMapInnerFragment) fragmentManager
        .findFragmentByTag(Constants.FragmentTags.MAP_INNER_MAP);
    if (mapListViewFragment == null
        || (fileName != null && !fileName.equals(mapListViewFragment.getFileName())) || createNew) {
      if (mapListViewFragment != null) {
        // remove the old fragment
        WebLogger.getLogger(getAppName())
            .d(TAG, "[showMapFragment] removing old map list fragment");
        fragmentTransaction.remove(mapListViewFragment);
      }
      WebLogger.getLogger(getAppName()).d(TAG, "[showMapFragment] creating new map list fragment");
      mapListViewFragment = this.createMapListViewFragment(fileName);
      fragmentTransaction.add(R.id.map_view_list, mapListViewFragment,
          Constants.FragmentTags.MAP_LIST);
    } else {
      WebLogger.getLogger(getAppName())
          .d(TAG, "[showMapFragment] existing map list fragment found");
      fragmentTransaction.show(mapListViewFragment);
    }
    if (innerMapFragment == null || createNew) {
      if (innerMapFragment != null) {
        // remove the old fragment
        WebLogger.getLogger(getAppName()).d(TAG,
            "[showMapFragment] removing old inner map fragment");
        fragmentTransaction.remove(innerMapFragment);
      }
      WebLogger.getLogger(getAppName()).d(TAG, "[showMapFragment] creating new inner map fragment");
      innerMapFragment = this.createInnerMapFragment();
      fragmentTransaction.add(R.id.map_view_inner_map, innerMapFragment,
          Constants.FragmentTags.MAP_INNER_MAP);
      innerMapFragment.listener = this;
    } else {
      WebLogger.getLogger(getAppName()).d(TAG,
          "[showMapFragment] existing inner map fragment found");
      innerMapFragment.listener = this;
      fragmentTransaction.show(innerMapFragment);
    }
    fragmentTransaction.commit();
  }

  /**
   * Create the {@link TableMapInnerFragment} that will be displayed as the map.
   * 
   * @return
   */
  TableMapInnerFragment createInnerMapFragment() {
    TableMapInnerFragment result = new TableMapInnerFragment();
    return result;
  }

  /**
   * Create the {@link MapListViewFragment} that will be displayed with the map
   * view.
   * 
   * @param listViewFileName
   *          the file name of the list view that will be displayed
   * @return
   */
  MapListViewFragment createMapListViewFragment(String listViewFileName) {
    MapListViewFragment result = new MapListViewFragment();
    Bundle listArguments = new Bundle();
    IntentUtil.addFileNameToBundle(listArguments, listViewFileName);
    result.setArguments(listArguments);
    return result;
  }

  public void showListFragment() throws RemoteException {
    this.showListFragment(false);
  }

  public void showListFragment(boolean createNew) throws RemoteException {
    this.setCurrentFragmentType(ViewFragmentType.LIST);
    this.updateChildViewVisibility(ViewFragmentType.LIST);
    // Try to use a passed file name. If one doesn't exist, try to use the
    // default.
    String fileName = IntentUtil.retrieveFileNameFromBundle(this.getIntent().getExtras());
    if (fileName == null) {
      OdkDbHandle db = null;
      try {
        db = Tables.getInstance().getDatabase().openDatabase(getAppName());
        fileName = TableUtil.get().getListViewFilename(Tables.getInstance(), getAppName(), db, getTableId());
      } finally {
        if (db != null) {
          Tables.getInstance().getDatabase().closeDatabase(getAppName(), db);
        }
      }
    }
    FragmentManager fragmentManager = this.getFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
    this.hideAllOtherViewFragments(ViewFragmentType.LIST, fragmentTransaction);
    ListViewFragment listViewFragment = (ListViewFragment) fragmentManager
        .findFragmentByTag(ViewFragmentType.LIST.name());
    if (listViewFragment == null || createNew) {
      if (listViewFragment == null) {
        WebLogger.getLogger(getAppName()).d(TAG,
            "[showListFragment] existing list fragment not found");
      } else {
        // remove the old fragment
        WebLogger.getLogger(getAppName()).d(TAG, "[showListFragment] removing old list fragment");
        fragmentTransaction.remove(listViewFragment);
      }
      listViewFragment = this.createListViewFragment(fileName);
      fragmentTransaction.add(R.id.activity_table_display_activity_one_pane_content,
          listViewFragment, ViewFragmentType.LIST.name());
    } else {
      WebLogger.getLogger(getAppName()).d(TAG, "[showListFragment] existing list fragment found");
      fragmentTransaction.show(listViewFragment);
    }
    fragmentTransaction.commit();
  }

  /**
   * Create a {@link ListViewFragment} to be used by the activity.
   * 
   * @param fileName
   *          the file name to be displayed
   */
  ListViewFragment createListViewFragment(String fileName) {
    ListViewFragment result = new ListViewFragment();
    Bundle arguments = new Bundle();
    IntentUtil.addFileNameToBundle(arguments, fileName);
    result.setArguments(arguments);
    return result;
  }

  public void showDetailFragment() throws RemoteException {
    this.showDetailFragment(false);
  }

  public void showDetailFragment(boolean createNew) throws RemoteException {
    this.setCurrentFragmentType(ViewFragmentType.DETAIL);
    this.updateChildViewVisibility(ViewFragmentType.DETAIL);
    FragmentManager fragmentManager = this.getFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
    this.hideAllOtherViewFragments(ViewFragmentType.DETAIL, fragmentTransaction);
    String fileName = IntentUtil.retrieveFileNameFromBundle(this.getIntent().getExtras());
    // Try and use the default.
    if (fileName == null) {
      WebLogger.getLogger(getAppName()).d(TAG, "[showDetailFragment] fileName not found in Intent");
      OdkDbHandle db = null;
      try {
        db = Tables.getInstance().getDatabase().openDatabase(getAppName());
        fileName = TableUtil.get().getDetailViewFilename(Tables.getInstance(), getAppName(), db, getTableId());
      } finally {
        if (db != null) {
          Tables.getInstance().getDatabase().closeDatabase(getAppName(), db);
        }
      }
    }
    String rowId = IntentUtil.retrieveRowIdFromBundle(this.getIntent().getExtras());
    // Try to retrieve one that already exists.
    DetailViewFragment detailViewFragment = (DetailViewFragment) fragmentManager
        .findFragmentByTag(ViewFragmentType.DETAIL.name());
    if (detailViewFragment == null || createNew) {
      if (detailViewFragment != null) {
        WebLogger.getLogger(getAppName()).d(TAG,
            "[showDetailViewFragment] removing old detail view fragment");
        fragmentTransaction.remove(detailViewFragment);
      } else {
        WebLogger.getLogger(getAppName()).d(TAG,
            "[showDetailViewFragment] no existing detail view fragment found");
      }
      detailViewFragment = this.createDetailViewFragment(fileName, rowId);

      fragmentTransaction.add(R.id.activity_table_display_activity_one_pane_content,
          detailViewFragment, ViewFragmentType.DETAIL.name());
    } else {
      WebLogger.getLogger(getAppName()).d(TAG,
          "[showDetailViewFragment] existing detail view fragment found");
      fragmentTransaction.show(detailViewFragment);
    }

    fragmentTransaction.commit();
  }

  /**
   * Create a {@link DetailViewFragment} to be used with the fragments.
   * 
   * @param fileName
   * @param rowId
   * @return
   */
  DetailViewFragment createDetailViewFragment(String fileName, String rowId) {
    DetailViewFragment result = new DetailViewFragment();
    Bundle bundle = new Bundle();
    IntentUtil.addRowIdToBundle(bundle, rowId);
    IntentUtil.addFileNameToBundle(bundle, fileName);
    result.setArguments(bundle);
    return result;
  }

  /**
   * Update the content view's children visibility for viewFragmentType. This is
   * required due to the fact that not all the fragments make use of the same
   * children views within the activity.
   * 
   * @param viewFragmentType
   */
  void updateChildViewVisibility(ViewFragmentType viewFragmentType) {
    // The map fragments occupy a different view than the single pane
    // content. This is because the map is two views--the list and the map
    // itself. So, we need to hide and show the others as appropriate.
    View onePaneContent = this.findViewById(R.id.activity_table_display_activity_one_pane_content);
    View mapContent = this.findViewById(R.id.activity_table_display_activity_map_content);
    switch (viewFragmentType) {
    case DETAIL:
    case LIST:
    case SPREADSHEET:
      onePaneContent.setVisibility(View.VISIBLE);
      mapContent.setVisibility(View.GONE);
      return;
    case MAP:
      onePaneContent.setVisibility(View.GONE);
      mapContent.setVisibility(View.VISIBLE);
      return;
    default:
      WebLogger.getLogger(getAppName()).e(TAG,
          "[updateChildViewVisibility] unrecognized type: " + viewFragmentType);
    }
  }

  /**
   * Retrieve the {@link DetailViewFragment} that is associated with this
   * activity.
   * 
   * @return the fragment, or null if it is not present
   */
  DetailViewFragment findDetailViewFragment() {
    FragmentManager fragmentManager = this.getFragmentManager();
    DetailViewFragment result = (DetailViewFragment) fragmentManager
        .findFragmentByTag(ViewFragmentType.DETAIL.name());
    return result;
  }

  /**
   * Return the {@link ViewFragmentType} that is currently being displayed.
   */
  public ViewFragmentType getCurrentFragmentType() {
    return this.mCurrentFragmentType;
  }

  /**
   * Invoked by TableMapInnerFragment when an item has been selected
   */
  @Override
  public void onSetSelectedItemIndex(int i) {
    MapListViewFragment mapListViewFragment = this.findMapListViewFragment();
    if (mapListViewFragment == null) {
      WebLogger.getLogger(getAppName()).e(TAG,
          "[onSetIndex] mapListViewFragment is null! Returning");
      return;
    } else {
      mapListViewFragment.setIndexOfSelectedItem(i);
    }
  }

  /**
   * Invoked by TableMapInnerFragment when an item has stopped being selected
   */
  public void setNoItemSelected() {
    MapListViewFragment mapListViewFragment = this.findMapListViewFragment();
    if (mapListViewFragment == null) {
      WebLogger.getLogger(getAppName()).e(TAG,
          "[setNoItemSelected] mapListViewFragment is null! Returning");
      return;
    } else {
      mapListViewFragment.setNoItemSelected();
    }
  }

  /**
   * Find a {@link MapListViewFragment} that is associated with this activity.
   * If not present, returns null.
   * 
   * @return
   */
  MapListViewFragment findMapListViewFragment() {
    FragmentManager fragmentManager = this.getFragmentManager();
    MapListViewFragment result = (MapListViewFragment) fragmentManager
        .findFragmentByTag(Constants.FragmentTags.MAP_LIST);
    return result;
  }

}
