/*
 * Copyright (C) 2012 University of Washington
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
package org.opendatakit.tables.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opendatakit.common.android.data.ColorRuleGroup;
import org.opendatakit.common.android.data.ColumnProperties;
import org.opendatakit.common.android.data.KeyValueHelper;
import org.opendatakit.common.android.data.KeyValueStoreHelper;
import org.opendatakit.common.android.data.Preferences;
import org.opendatakit.tables.views.components.LockableHorizontalScrollView;
import org.opendatakit.tables.views.components.LockableScrollView;

import android.content.Context;
import android.view.ContextMenu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * A view similar to a spreadsheet. Builds TabularViews for the header and body
 * (builds two sets of these if a column is frozen to the left).
 * <p>
 * SS: I made some changes to this to try and make scrolling
 * more efficient. I am leaving some of the seemingly unreferenced and now
 * unnecessary methods/fields in case changes someone has made to this class
 * in parallel rely on these changes.
 * @author sudar.sam@gmail.com
 * @author unknown
 */
public class SpreadsheetView extends LinearLayout
        implements TabularView.Controller {

  private static final String TAG = "SpreadsheetView";

  // moved this from the old TableViewSettings
  public static final int DEFAULT_COL_WIDTH = 125;

  /******************************
   * These are constants needed for the key value store.
   ******************************/
  public static final String KVS_PARTITION = "SpreadsheetView";
  public static final String KVS_ASPECT_DEFAULT = "default";
  // So this key should go into the column aspect, b/c it is a column key value
  // entry that needs to be associated with a single column, but also needs
  // to have this naming convention to avoid namespace collisions.
  public static final String KEY_COLUMN_WIDTH = "SpreadsheetView.columnWidth";
  public static final String DEFAULT_KEY_COLUMN_WIDTHS =
      Integer.toString(DEFAULT_COL_WIDTH);

    private static final int MIN_CLICK_DURATION = 0;
    private static final int MIN_LONG_CLICK_DURATION = 1000;

    private final Context context;
    private final Controller controller;
    private final SpreadsheetUserTable table;
    private final int fontSize;

    private final Map<String, ColumnProperties> mElementKeyToProperties;
    private final Map<String, ColorRuleGroup> mElementKeyToColorRuleGroup;

    // Keeping this for now in case someone else needs to work with the code
    // and relied on this variable.
    private LockableScrollView dataScroll;
    private LockableScrollView dataStatusScroll;
    private View wrapper;
    private HorizontalScrollView wrapScroll;

    private LockableScrollView indexScroll;
    private LockableScrollView mainScroll;
    private TabularView indexData;
    private TabularView indexHeader;
    private TabularView mainData;
    private TabularView mainHeader;

    private View.OnTouchListener mainDataCellClickListener;
    private View.OnTouchListener mainHeaderCellClickListener;
    private View.OnTouchListener indexDataCellClickListener;
    private View.OnTouchListener indexHeaderCellClickListener;

    private int lastLongClickedCellId;

    public SpreadsheetView(Context context, Controller controller,
        SpreadsheetUserTable table) {
        super(context);
        this.context = context;
        this.controller = controller;
        this.table = table;

        // TODO: figure out if we can invalidate a screen region
        // to get it to render the screen rather than
        // disabling the hardware acceleration on this view.
        // Disable it so that you don't have to tap the screen to
        // after a scroll action to see the new portion of the
        // spreadsheet.
        this.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // We have to initialize the items that will be shared across the
        // TabularView objects.
        this.mElementKeyToColorRuleGroup =
            new HashMap<String, ColorRuleGroup>();
        this.mElementKeyToProperties = table.getAllColumns();
        for (ColumnProperties cp : mElementKeyToProperties.values()) {
          mElementKeyToColorRuleGroup.put(cp.getElementKey(),
              table.getColumnColorRuleGroup(cp.getElementKey()));
          mElementKeyToProperties.put(cp.getElementKey(),cp);
        }

        // if a custom font size is defined in the KeyValueStore, use that
        // if not, use the general font size defined in preferences
        KeyValueStoreHelper kvsh = table.getKeyValueStoreHelper("SpreadsheetView");
        if (kvsh.getInteger("fontSize") == null)
        	fontSize = (new Preferences(context, table.getAppName())).getFontSize();
        else
        	fontSize = kvsh.getInteger("fontSize");

        initListeners();
        if (!table.isIndexed()) {
            buildNonIndexedTable();
        } else {
            buildIndexedTable();
            indexData.setOnTouchListener(indexDataCellClickListener);
            indexHeader.setOnTouchListener(indexHeaderCellClickListener);
        }
        mainData.setOnTouchListener(mainDataCellClickListener);
        mainHeader.setOnTouchListener(mainHeaderCellClickListener);
    }

    /**
     * Initializes the click listeners.
     */
    private void initListeners() {
        mainDataCellClickListener = new CellTouchListener() {
            @Override
            protected int figureCellId(int x, int y) {
                int cellNum = mainData.getCellNumber(x, y);
//                Log.d(TAG, "mainDataCellClickListener cellId: " + cellNum);
                if (!table.isIndexed()) {
                    return cellNum;
                } else {
                  return cellNum;
                }
            }
            @Override
            protected void takeDownAction(int cellId) {
              if (table.isIndexed()) {
                indexData.highlight(-1);
              }
                mainData.highlight(cellId);
            }
            @Override
            protected void takeClickAction(int cellId) {
                controller.regularCellClicked(cellId);
            }
            @Override
            protected void takeLongClickAction(int cellId, int rawX,
                    int rawY) {
              boolean isIndexed = table.isIndexed();
                lastLongClickedCellId = cellId;
                // So we need to check for whether or not the table is indexed again and
                // alter the cellId appropriately.
                if (isIndexed) {
                  int colNum = cellId % (table.getWidth() - 1);
                  int rowNum = cellId / (table.getWidth() - 1);
                  cellId = cellId + rowNum + ((colNum < table.retrieveIndexedColumnOffset()) ? 0 : 1);
                }
                controller.regularCellLongClicked(cellId, rawX, rawY);
            }
            @Override
            protected void takeDoubleClickAction(int cellId, int rawX,
                int rawY) {
              boolean isIndexed = table.isIndexed();
              // So it seems like the cellId is coming from the mainData table, which
              // does NOT include the index. So to get the right row here we actually
              // have to perform a little extra.
              int trueCellId = cellId;
              if (isIndexed) {
                int colNum = cellId % (table.getWidth() - 1);
                int rowNum = cellId / (table.getWidth() - 1);
                // trying to hack together correct thing for overlay
                trueCellId = rowNum * table.getWidth() +
                    colNum + ((colNum < table.retrieveIndexedColumnOffset()) ? 0 : 1);
              }
              controller.regularCellDoubleClicked(trueCellId, rawX, rawY);
            }
        };
        mainHeaderCellClickListener = new CellTouchListener() {
            @Override
            protected int figureCellId(int x, int y) {
                int cellNum = mainHeader.getCellNumber(x, y);
                if (!table.isIndexed()) {
                    return cellNum;
                } else {
                    int colNum = cellNum % (table.getWidth() - 1);
                    return cellNum + ((colNum < table.retrieveIndexedColumnOffset()) ? 0 : 1);
                }
            }
            @Override
            protected void takeDownAction(int cellId) {}
            @Override
            protected void takeClickAction(int cellId) {
                mainData.highlight(-1);
                controller.headerCellClicked(cellId);
            }
            @Override
            protected void takeLongClickAction(int cellId, int rawX,
                    int rawY) {
                lastLongClickedCellId = cellId;
                controller.openContextMenu(mainHeader);
            }
            /**
             * Make this do the same thing as a long click.
             */
            @Override
            protected void takeDoubleClickAction(int cellId, int rawX,
                int rawY) {
              takeLongClickAction(cellId, rawX, rawY);
            }
        };
        indexDataCellClickListener = new CellTouchListener() {
            @Override
            protected int figureCellId(int x, int y) {
                int cellNum = indexData.getCellNumber(x, y);
//                Log.d(TAG, "indexDataCellClickListener cellNum: " + cellNum);
                return cellNum;
            }
            @Override
            protected void takeDownAction(int cellId) {
              mainData.highlight(-1);
              indexData.highlight(cellId);
            }
            @Override
            protected void takeClickAction(int cellId) {
                mainData.highlight(-1);
                controller.indexedColCellClicked(cellId);
            }
            @Override
            protected void takeLongClickAction(int cellId, int rawX,
                    int rawY) {
                // here it's just the row plus the number of the indexed column.
                // So the true cell id is the cellId parameter, which is essentially the
                // row number, * the width of the table, plus the indexed col
                int trueNum =
                    cellId *
                    table.getWidth() +
                    table.retrieveIndexedColumnOffset();
                lastLongClickedCellId = trueNum;
                controller.indexedColCellLongClicked(trueNum, rawX, rawY);
            }

            @Override
            protected void takeDoubleClickAction(int cellId, int rawX,
                int rawY) {
              // Ok, so here the cellId is also the row number, as we only allow a
              // single indexed column atm. So if you double click the 5th cell, it will
              // also have to be the 5th row.
              int trueNum =
                  cellId *
                  table.getWidth() +
                  table.retrieveIndexedColumnOffset();
              controller.indexedColCellDoubleClicked(trueNum, rawX, rawY);
            }
        };
        indexHeaderCellClickListener = new CellTouchListener() {
            @Override
            protected int figureCellId(int x, int y) {
                return table.retrieveIndexedColumnOffset();
            }
            @Override
            protected void takeDownAction(int cellId) {}
            @Override
            protected void takeClickAction(int cellId) {
                mainData.highlight(-1);
                controller.headerCellClicked(cellId);
            }
            @Override
            protected void takeLongClickAction(int cellId, int rawX,
                    int rawY) {
                lastLongClickedCellId = cellId;
                controller.openContextMenu(indexHeader);
            }
            /**
             * Do the same thing as a long click.
             */
            @Override
            protected void takeDoubleClickAction(int cellId, int rawX,
                int rawY) {
              takeLongClickAction(cellId, rawX, rawY);
            }
        };
    }

    private void buildNonIndexedTable() {
		wrapper = buildTable(-1, false);
		wrapScroll = new HorizontalScrollView(context);
		wrapScroll.addView(wrapper, LinearLayout.LayoutParams.WRAP_CONTENT,
		    LinearLayout.LayoutParams.MATCH_PARENT);
		/*** this was all here before ***/
		LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
		    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
		wrapLp.weight = 1;
		wrapScroll.setHorizontalFadingEdgeEnabled(true); // works

		LinearLayout completeWrapper = new LinearLayout(context);
		View statusWrapper = buildStatusTable();
		statusWrapper.setHorizontalFadingEdgeEnabled(true);
		statusWrapper.setVerticalFadingEdgeEnabled(true);
		completeWrapper.addView(statusWrapper);
		completeWrapper.addView(wrapScroll);
		completeWrapper.setHorizontalFadingEdgeEnabled(true);
		completeWrapper.setVerticalFadingEdgeEnabled(true);

		addView(completeWrapper, wrapLp);
      mainScroll.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            dataStatusScroll.scrollTo(dataStatusScroll.getScrollX(),
                view.getScrollY());
            if (event.getAction() == MotionEvent.ACTION_UP) {
              mainScroll.startScrollerTask();
            }
            return false;
        }
    });
    }

    private void buildIndexedTable() {
        int indexedCol = table.retrieveIndexedColumnOffset();
        View mainWrapper = buildTable(indexedCol, false);
        View indexWrapper = buildTable(indexedCol, true);
        wrapScroll = new LockableHorizontalScrollView(context);
        wrapScroll.addView(mainWrapper, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        wrapScroll.setHorizontalFadingEdgeEnabled(true);
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.addView(indexWrapper);
        wrapper.addView(wrapScroll);

        LinearLayout completeWrapper = new LinearLayout(context);
		View statusWrapper = buildStatusTable();
		completeWrapper.addView(statusWrapper);
		completeWrapper.addView(wrapper);

		addView(completeWrapper);

        indexScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                mainScroll.scrollTo(mainScroll.getScrollX(),
                        view.getScrollY());
                dataStatusScroll.scrollTo(mainScroll.getScrollX(),
                    view.getScrollY());
                if (event.getAction() == MotionEvent.ACTION_UP) {
                  indexScroll.startScrollerTask();
                  mainScroll.startScrollerTask();
                }
                return false;
            }
        });
        indexScroll.setOnScrollStoppedListener(new
            LockableScrollView.OnScrollStoppedListener() {

              @Override
              public void onScrollStopped() {
//                Log.i(TAG, "stopped in onStopped of indexScroll");
              }
            });
        mainScroll.setOnScrollStoppedListener(new
            LockableScrollView.OnScrollStoppedListener() {

              @Override
              public void onScrollStopped() {
//                Log.i(TAG, "stopped in onStopped of mainScroll");

              }
            });
        mainScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                indexScroll.scrollTo(indexScroll.getScrollX(),
                        view.getScrollY());
                dataStatusScroll.scrollTo(indexScroll.getScrollX(),
                    view.getScrollY());
                if (event.getAction() == MotionEvent.ACTION_UP) {
                  indexScroll.startScrollerTask();
                  mainScroll.startScrollerTask();
                }
                return false;
            }
        });
    }

    /**
     * Builds a (piece of a) table. The table may either be the indexed column
     * of an indexed table, the non-indexed columns of an indexed table, or the
     * entirety of an unindexed table.
     * @param indexedCol the column that is indexed (or -1)
     * @param isIndexed whether this table is for the indexed column
     * @return a view including the header and body of the table
     */
    private View buildTable(int indexedCol, boolean isIndexed) {
//      Log.i(TAG, "entering buildTable. indexedCol: " + indexedCol +
//          "isIndexed: " + isIndexed);
        List<String> elementKeysToDisplay =
            new ArrayList<String>(table.getNumberOfDisplayColumns());
        int[] colWidths;
        int[] completeColWidths = getColumnWidths();
        TabularView dataTable;
        TabularView headerTable;
        if (isIndexed) {
        	ColumnProperties cp = table.getColumnByIndex(indexedCol);
            elementKeysToDisplay.add(cp.getElementKey());
            colWidths = new int[1];
            colWidths[0] = completeColWidths[indexedCol];
            dataTable = TabularView.getIndexDataTable(context, this,
                table, elementKeysToDisplay, colWidths, fontSize,
                this.mElementKeyToProperties,
                this.mElementKeyToColorRuleGroup);
            headerTable = TabularView.getIndexHeaderTable(context,
                this, table, elementKeysToDisplay, colWidths, fontSize,
                this.mElementKeyToProperties,
                this.mElementKeyToColorRuleGroup);
        } else {
            int width = (indexedCol < 0) ? table.getWidth() :
                table.getWidth() - 1;
            colWidths = new int[width];
            int addIndex = 0;
            for (int i = 0; i < table.getWidth(); i++) {
                if (i == indexedCol) {
                    continue;
                }
                ColumnProperties cp = table.getColumnByIndex(i);
                elementKeysToDisplay.add(cp.getElementKey());
                colWidths[addIndex] = completeColWidths[i];
                addIndex++;
            }
            dataTable = TabularView.getMainDataTable(context, this,
                table, elementKeysToDisplay, colWidths, fontSize,
                this.mElementKeyToProperties,
                this.mElementKeyToColorRuleGroup);
            headerTable = TabularView.getMainHeaderTable(context,
                this, table, elementKeysToDisplay, colWidths, fontSize,
                this.mElementKeyToProperties,
                this.mElementKeyToColorRuleGroup);
        }
        dataScroll = new LockableScrollView(context);
        dataScroll.addView(dataTable, new ViewGroup.LayoutParams(
                dataTable.getTableWidth(), dataTable.getTableHeight()));
        dataScroll.setVerticalFadingEdgeEnabled(true);
        dataScroll.setHorizontalFadingEdgeEnabled(true);
        if (isIndexed) {
            indexData = dataTable;
            indexHeader = headerTable;
            indexScroll = dataScroll;
        } else {
            mainData = dataTable;
            mainHeader = headerTable;
            mainScroll = dataScroll;
        }
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(headerTable, headerTable.getTableWidth(),
                headerTable.getTableHeight());
        LinearLayout.LayoutParams dataLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        dataLp.weight = 1;
        wrapper.addView(dataScroll, dataLp);
        return wrapper;
    }


    private View buildStatusTable() {
        int[] colWidths;
        List<String> dummyHeaderElementKeys = new ArrayList<String>();
        dummyHeaderElementKeys.add("header");
        List<String> dummyDataElementKeys = new ArrayList<String>();
        dummyDataElementKeys.add("data");
        colWidths = new int[1];
        colWidths[0] = TabularView.DEFAULT_STATUS_COLUMN_WIDTH;

        dataStatusScroll = new LockableScrollView(context);
        TabularView dataTable = TabularView.getStatusDataTable(context,
            this, table, colWidths, fontSize,
            this.mElementKeyToProperties, this.mElementKeyToColorRuleGroup);
        dataTable.setVerticalFadingEdgeEnabled(true);
        dataTable.setVerticalScrollBarEnabled(false);
        dataStatusScroll.addView(dataTable, new ViewGroup.LayoutParams(
                dataTable.getTableWidth(), dataTable.getTableHeight()));
        dataStatusScroll.setVerticalFadingEdgeEnabled(true);
        dataStatusScroll.setHorizontalFadingEdgeEnabled(true);
        TabularView headerTable = TabularView.getStatusHeaderTable(context,
            this, table, colWidths, fontSize,
            this.mElementKeyToProperties, this.mElementKeyToColorRuleGroup);
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(headerTable, headerTable.getTableWidth(),
                headerTable.getTableHeight());
        LinearLayout.LayoutParams dataLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        dataLp.weight = 1;
        wrapper.addView(dataStatusScroll, dataLp);
        wrapper.setVerticalFadingEdgeEnabled(true);
        wrapper.setHorizontalFadingEdgeEnabled(true);
        return wrapper;
    }

    /**
     * Gets the x translation of the scroll. This is in particular how far
     * you have scrolled to look at columns that do not begin onscreen.
     * @return
     */
    @Override
    public int getMainScrollX() {
      // this is getting the correct x
      int result = this.wrapScroll.getScrollX();
      return result;
    }

    /**
     * Gets the y translation of the scroll. This is in particular the y
     * offset for the actual scrolling of the rows, so that a positive
     * offset will indicate that you have scrolled to some non-zero row.
     * @return
     */
    @Override
    public int getMainScrollY() {
      // this is getting the correct y
      int result = this.mainScroll.getScrollY();
      return result;
    }

    @Override
    public void onCreateMainDataContextMenu(ContextMenu menu) {
        controller.prepRegularCellOccm(menu, lastLongClickedCellId);
    }

    @Override
    public void onCreateIndexDataContextMenu(ContextMenu menu) {
        controller.prepIndexedColCellOccm(menu, lastLongClickedCellId);
    }

    @Override
    public void onCreateHeaderContextMenu(ContextMenu menu) {
        controller.prepHeaderCellOccm(menu, lastLongClickedCellId);
    }

    private abstract class CellTouchListener implements View.OnTouchListener {

        private static final int MAX_DOUBLE_CLICK_TIME = 500;

        private long lastDownTime;

        public CellTouchListener() {
            lastDownTime = -1;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            int x = (Float.valueOf(event.getX())).intValue();
            int y = (Float.valueOf(event.getY())).intValue();
            int cellId = figureCellId(x, y);
            long duration = event.getEventTime() - event.getDownTime();
            if (event.getAction() == MotionEvent.ACTION_UP &&
                    duration >= MIN_CLICK_DURATION) {
                if (event.getEventTime() - lastDownTime <
                        MAX_DOUBLE_CLICK_TIME) {
                    takeDoubleClickAction(cellId,
                        (Float.valueOf(event.getRawX())).intValue(),
                        (Float.valueOf(event.getRawY())).intValue());
                } else if (duration < MIN_LONG_CLICK_DURATION) {
                    takeClickAction(cellId);
                } else {
                    int rawX = (Float.valueOf(event.getRawX())).intValue();
                    int rawY = (Float.valueOf(event.getRawY())).intValue();
                    takeLongClickAction(cellId, rawX, rawY);
                }
                lastDownTime = event.getDownTime();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                takeDownAction(cellId);
                return true;
            } else {
                return false;
            }
        }

        protected abstract int figureCellId(int x, int y);

        protected abstract void takeDownAction(int cellId);

        protected abstract void takeClickAction(int cellId);

        protected abstract void takeLongClickAction(int cellId, int rawX,
                int rawY);

        protected abstract void takeDoubleClickAction(int cellId, int rawX,
            int rawY);
    }

    public interface Controller {

        public void regularCellClicked(int cellId);

        public void headerCellClicked(int cellId);

        public void indexedColCellClicked(int cellId);

        public void regularCellLongClicked(int cellId, int rawX, int rawY);

        public void regularCellDoubleClicked(int cellId, int rawX, int rawY);

        public void indexedColCellDoubleClicked(int cellId, int rawX,
            int rawY);

        public void indexedColCellLongClicked(int cellId, int rawX, int rawY);

        public void openContextMenu(View view);

        public void prepRegularCellOccm(ContextMenu menu, int cellId);

        public void prepHeaderCellOccm(ContextMenu menu, int cellId);

        public void prepIndexedColCellOccm(ContextMenu menu, int cellId);

    }

    /**
     * Get the column widths for the table. The values in the array match the
     * order specified in the column order.
     * <p>
     * NB: If getting this from outside of spreadsheet view, you should really
     * consider if you need to be accessing column widths.
     * @return
     */
    public int[] getColumnWidths() {
      // So what we want to do is go through and get the column widths for each
      // column. A problem here is that there is no caching, and if you have a
      // lot of columns you're really working the gut of the database.
      int numberOfDisplayColumns = table.getNumberOfDisplayColumns();
      int[] columnWidths = new int[numberOfDisplayColumns];
      KeyValueStoreHelper columnKVSH =
          table.getKeyValueStoreHelper(ColumnProperties.KVS_PARTITION);
      for (int i = 0; i < numberOfDisplayColumns; i++) {
    	ColumnProperties cp = table.getColumnByIndex(i);
        String elementKey = cp.getElementKey();
        KeyValueHelper aspectHelper = columnKVSH.getAspectHelper(elementKey);
        Integer value =
            aspectHelper.getInteger(SpreadsheetView.KEY_COLUMN_WIDTH);
        if (value == null) {
          columnWidths[i] = DEFAULT_COL_WIDTH;
        } else {
          columnWidths[i] = value;
        }
      }
      return columnWidths;
    }
}