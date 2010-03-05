package com.kpz.pomodorotasks.activity;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

import com.kpz.pomodorotasks.map.TaskDatabaseMap;
import com.kpz.pomodorotasks.map.TaskDatabaseMap.StatusType;

public class TaskBrowserActivity extends ListActivity {
    
	private static final String LOG_TAG = "PomodoroTasks";

	private static final int NOTIFICATION_ID = R.layout.task_list;
	
	private static final int ACTIVITY_EDIT = 1;
	private static final int ACTIVITY_SET_OPTIONS = 2;
	
	private static final int MAIN_MENU_DELETE_ALL_ID = Menu.FIRST;
	private static final int MAIN_MENU_DELETED_COMPLETED_ID = MAIN_MENU_DELETE_ALL_ID + 1;
	private static final int MAIN_MENU_OPTIONS_ID = MAIN_MENU_DELETE_ALL_ID + 2;
	private static final int MAIN_MENU_QUIT_ID = MAIN_MENU_DELETE_ALL_ID + 3;
	private static final int MAIN_MENU_ADD_TASK_ID = MAIN_MENU_DELETE_ALL_ID + 4;
	
	private TaskPanel taskPanel;
	private ListView taskList;
    private TaskDatabaseMap taskDatabaseMap;
	private Cursor taskListCursor;
	private Long currentTaskRowId = null;

	private ServiceConnection connection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        initView();
    }

	private boolean isCurrentOrientationLandscape() {
		return getBaseContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
	}
    
    @Override
    protected void onDestroy() {

    	if (connection != null){
    		unbindService(connection);    		
    	}
    	
    	stopService(new Intent(TaskBrowserActivity.this, 
                NotifyingService.class));
    	
    	super.onDestroy();
    }
    
	private void initView() {

		setContentView(NOTIFICATION_ID);
        
        initDatabaseHelper();        
        initTasksList();
        initAddTaskPanel();
        refreshTaskPanelForOrientation();
        initAndHideRunTaskPanel();   
	}

	private void initAndHideRunTaskPanel() {
		
		taskPanel = new TaskPanel(this, taskDatabaseMap);
		taskPanel.hidePanel();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		// doing almost nothing to the view when screen orientation changes except...
		refreshTaskPanelForOrientation();
	}
	
	private void refreshTaskPanelForOrientation() {

		if (isCurrentOrientationLandscape()){

			addTaskPanel.setVisibility(View.GONE);
			hideAddPanelButton.setVisibility(View.VISIBLE);
			
			
		} else {
			
			hideAddPanelButton.setVisibility(View.GONE);
			addTaskPanel.setVisibility(View.VISIBLE);
		}
	}

	private void startTask(String taskDescription) {

		taskPanel.startTask(taskDescription);
	}
	
	private void initTasksList() {
		
		rootLayout = (LinearLayout)findViewById(R.id.screen);
		initTasksListViewContainer();
        populateTasksList();
	}
	
    private void populateTasksList() {
    	
    	Cursor tasksCursor = taskDatabaseMap.fetchAll();
        startManagingCursor(tasksCursor);
        
        // Create an array to specify the fields we want to display in the list (only Description)
        String[] from = new String[]{TaskDatabaseMap.KEY_DESCRIPTION, TaskDatabaseMap.KEY_STATUS};
        
        // and an array of the fields we want to bind those fields to (in this case just task_description)
        int[] to = new int[]{R.id.task_description, R.id.taskRow};
        
        SimpleCursorAdapter taskListCursorAdapter = new SimpleCursorAdapter(getApplication(), R.layout.task_row, tasksCursor, from, to);
        taskListCursorAdapter.setViewBinder(new ViewBinder() {
			
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				
				if (view.getId() == R.id.taskRow){

					TextView textView = (TextView)view.findViewById(R.id.task_description);
					
					String statusText = cursor.getString(columnIndex);
					if (StatusType.OPEN.getDescription().equals(statusText)){
						textView.getPaint().setStrikeThruText(false);
					} else if (StatusType.COMPLETED.getDescription().equals(statusText)){
						textView.getPaint().setStrikeThruText(true);
					}
					
					return true;
				}
				
				return false;
			}
		});
        
        setListAdapter(taskListCursorAdapter);
        taskListCursor = taskListCursorAdapter.getCursor();
    }

	private void initTasksListViewContainer() {
		taskList = getListView();
        ((TouchInterceptor) taskList).setDropListener(mDropListener);
        ((TouchInterceptor) taskList).setCheckOffListener(mCheckOffListener);
        taskList.setCacheColorHint(0);
	}
	
	public void checkOffTask(int which, View targetView) {

		TextView textView = (TextView)targetView.findViewById(R.id.task_description);
    	textView.getPaint().setStrikeThruText(true);
    	
    	Cursor cursor = (Cursor)getListAdapter().getItem(which);
		int rowId = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseMap.KEY_ROWID)));
		taskDatabaseMap.updateStatus(rowId, true);
		refreshTaskList();
	}
	
    private boolean refreshTaskList() {

    	return taskListCursor.requery();
	}

	public void uncheckOffTask(int which, View targetView) {

    	TextView textView = (TextView)targetView.findViewById(R.id.task_description);
    	textView.getPaint().setStrikeThruText(false);
    	
    	Cursor cursor = (Cursor)getListAdapter().getItem(which);
		int rowId = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseMap.KEY_ROWID)));
		taskDatabaseMap.updateStatus(rowId, false);
		refreshTaskList();
	}
	
    private TouchInterceptor.CheckOffListener mCheckOffListener = new TouchInterceptor.CheckOffListener() {
    	
        public void checkOff(int which) {

        	View targetView = (View)taskList.getChildAt(which - taskList.getFirstVisiblePosition());
        	checkOffTask(which, targetView);
        }
        
		public void uncheckOff(int which) {

			View targetView = (View)taskList.getChildAt(which - taskList.getFirstVisiblePosition());
			uncheckOffTask(which, targetView);
		}
    };

	private void initDatabaseHelper() {
		taskDatabaseMap = new TaskDatabaseMap(this);
        taskDatabaseMap.open();
	}

	private void initAddTaskPanel() {
		
		addTaskPanel = (LinearLayout)findViewById(R.id.add_task_panel);
		addTaskInputBox = (EditText) findViewById(R.id.add_task_input_box);

		final Button addButton = (Button) findViewById(R.id.add_task_input_button);
        addButton.setOnClickListener(new OnClickListener() {
            
        	public void onClick(View v) {
 
        		String noteDescription = addTaskInputBox.getText().toString().trim();
        		if (!noteDescription.equals("")){
        			createNewTask(noteDescription);
                    refreshTaskList();
                    resetAddTaskInputBox();        			
        		}
            }

			private void createNewTask(final String noteDescription) {
            	taskDatabaseMap.createTask(noteDescription);
			}
        });
        
        hideAddPanelButton = (ImageButton) findViewById(R.id.hide_add_panel_button);
        hideAddPanelButton.setVisibility(View.GONE);
        hideAddPanelButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				
				addTaskPanel.setVisibility(View.GONE);
			}
        });	
	}
    
	private void resetAddTaskInputBox() {
		
		addTaskInputBox.setText("");
		addTaskInputBox.requestFocus();
		
//to hide keyboard				
//        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(leftTextEdit.getWindowToken(), 0); 
//to display on-screen keyboard                 
//        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))  
//        .showSoftInput(editText, 0);  
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
		addTaskMenuItem = menu.add(0, MAIN_MENU_ADD_TASK_ID, 0, R.string.menu_add_task);
        addTaskMenuItem.setIcon(drawable.ic_menu_add);
        
        MenuItem menuItem = menu.add(0, MAIN_MENU_DELETED_COMPLETED_ID, 0, R.string.menu_delete_completed);
        menuItem.setIcon(drawable.ic_menu_agenda);
        
        menuItem = menu.add(0, MAIN_MENU_DELETE_ALL_ID, 0, R.string.menu_delete_all);
        menuItem.setIcon(drawable.ic_menu_delete);
        
        menuItem = menu.add(0, MAIN_MENU_OPTIONS_ID, 0, R.string.menu_options);
        menuItem.setIcon(drawable.ic_menu_preferences);
        
        menuItem = menu.add(0, MAIN_MENU_QUIT_ID, 0, R.string.menu_quit);
        menuItem.setIcon(drawable.ic_lock_power_off);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

    	if (isCurrentOrientationLandscape()){

    		resetAddTaskInputBox();
    		addTaskMenuItem.setVisible(true);
    		addTaskMenuItem.setEnabled(true);
    		
    	} else {

    		addTaskMenuItem.setVisible(false);
    		addTaskMenuItem.setEnabled(false);
    	}
    	
    	return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()) {

        case MAIN_MENU_ADD_TASK_ID:

        	addTaskPanel.setVisibility(View.VISIBLE);
        	return true;
        	
        case MAIN_MENU_DELETED_COMPLETED_ID:
        	taskDatabaseMap.deleteCompleted();
	        refreshTaskList();
			if (!isTaskExists(taskPanel.getCurrentTaskText())){
				taskPanel.refreshTaskPanel();
			}
        	return true;
        	
        case MAIN_MENU_DELETE_ALL_ID:
        	taskDatabaseMap.deleteAll();
	        refreshTaskList();
			if (!isTaskExists(taskPanel.getCurrentTaskText())){
				taskPanel.refreshTaskPanel();				
			}
	        return true;
        	
        case MAIN_MENU_OPTIONS_ID:
        	Intent i = new Intent(this, SettingsActivity.class);
	        startActivityForResult(i, ACTIVITY_SET_OPTIONS);
        	return true;        	
        
        case MAIN_MENU_QUIT_ID:
        	finish();
        	return true;    
        }
       
        return super.onMenuItemSelected(featureId, item);
    }

	@Override
    protected void onListItemClick(ListView l, final View v, final int position, long id) {
        
    	super.onListItemClick(l, v, position, id);

    	Cursor cursor = (Cursor)getListAdapter().getItem(new Long(position).intValue());
    	final Long rowId = new Long(cursor.getString(cursor.getColumnIndex(TaskDatabaseMap.KEY_ROWID)));
		
    	final String[] items = {"Start", "Edit", "Delete"};
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setItems(items, new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int item) {
		        
		    	//Toast.makeText(getApplicationContext(), items[item], Toast.LENGTH_SHORT).show();
		        
		    	switch (item) {
				case 0:
			        TextView textView = (TextView)v.findViewById(R.id.task_description);
			        startTask(textView.getText().toString());
			        currentTaskRowId = rowId;
					break;
					
				case 1:
					Intent i = new Intent(TaskBrowserActivity.this, TaskEditActivity.class);
			        i.putExtra(TaskDatabaseMap.KEY_ROWID, rowId);
			        startActivityForResult(i, ACTIVITY_EDIT);
			        break;
			        
				case 2:
			        taskDatabaseMap.delete(rowId);
			        refreshTaskList();
			        break;
			        
				default:
					break;
				}
		        
		    }
		});
		AlertDialog alert = builder.create();
		alert.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, 
                                    Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        
        switch (requestCode) {
			case ACTIVITY_SET_OPTIONS:
				
				taskPanel.resetTimeLeftIfTaskNotRunning();
				break;
			case ACTIVITY_EDIT:
				refreshTaskList();
				updateTaskPanel();
				break;
		}
    }

	private void updateTaskPanel() {
		if (currentTaskRowId != null){
			Cursor task = taskDatabaseMap.fetch(currentTaskRowId);
			String taskDescription = task.getString(task.getColumnIndexOrThrow(TaskDatabaseMap.KEY_DESCRIPTION));
			taskPanel.updateTaskDescription(taskDescription);
		}
	}
    
	public boolean isTaskExists(String text) {

		if (text == null || text.equals("")){
    		return false;
    	}
		
		boolean exists = false;
    	final int count = getListAdapter().getCount();
        for (int i = count - 1; i >= 0; i--) {

        	Cursor cursor = (Cursor)getListAdapter().getItem(i);
        	String taskDescription = cursor.getString(cursor.getColumnIndex(TaskDatabaseMap.KEY_DESCRIPTION));
            if(taskDescription.equals(text)){
				exists = true;
            }
        }
		return exists;
	}
    
    private TouchInterceptor.DropListener mDropListener = new TouchInterceptor.DropListener() {

    	public void drop(int from, int to) {

    		move(from, to);
        	resetBottomMargin();
        	refreshTaskList();
        }

		private void resetBottomMargin() {
			LinearLayout.LayoutParams viewGroupParams = (LinearLayout.LayoutParams)getListView().getLayoutParams();
			if (viewGroupParams.bottomMargin != 0){
				viewGroupParams.bottomMargin = 0;
	    		getListView().setLayoutParams(viewGroupParams);				
			}
		}

		private void move(int from, int to) {
			
			Cursor cursor = (Cursor)getListAdapter().getItem(from);
        	int fromSeq = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseMap.KEY_SEQUENCE)));

        	cursor = (Cursor)getListAdapter().getItem(to);
        	int toSeq = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseMap.KEY_SEQUENCE)));
    		int toRowId = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseMap.KEY_ROWID)));
        	
        	taskDatabaseMap.move(fromSeq, toRowId, toSeq);
		}
    };

	private LinearLayout rootLayout;

	private MenuItem addTaskMenuItem;

	private LinearLayout addTaskPanel;

	private ImageButton hideAddPanelButton;

	private EditText addTaskInputBox;
}
