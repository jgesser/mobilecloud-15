package vandy.mooc.presenter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.SimpleCursorAdapter;
import vandy.mooc.R;
import vandy.mooc.common.ConfigurableOps;
import vandy.mooc.common.ContextView;
import vandy.mooc.common.GenericAsyncTask;
import vandy.mooc.common.GenericAsyncTaskOps;
import vandy.mooc.common.Utils;
import vandy.mooc.model.mediator.VideoDataMediator;
import vandy.mooc.model.mediator.webdata.Video;
import vandy.mooc.model.provider.VideoContract.VideoEntry;
import vandy.mooc.model.services.UploadVideoService;

/**
 * Provides all the Video-related operations. It implements ConfigurableOps so it can be created/managed by the
 * GenericActivity framework. It extends GenericAsyncTaskOps so its doInBackground() method runs in a background task.
 * It plays the role of the "Abstraction" in Bridge pattern and the role of the "Presenter" in the Model-View-Presenter
 * pattern.
 */
public class VideoOps implements GenericAsyncTaskOps<Void, Void, Cursor>, ConfigurableOps<VideoOps.View> {
	/**
	 * Debugging tag used by the Android logger.
	 */
	private static final String TAG = VideoOps.class.getSimpleName();

	/**
	 * This interface defines the minimum interface needed by the VideoOps class in the "Presenter" layer to interact
	 * with the VideoListActivity in the "View" layer.
	 */
	public interface View extends ContextView {
		/**
		 * Finishes the Activity the VideoOps is associated with.
		 */
		void finish();

		/**
		 * Sets the Adapter that contains List of Videos.
		 */
		void setAdapter(SimpleCursorAdapter videoAdapter);
	}

	/**
	 * Used to enable garbage collection.
	 */
	private WeakReference<VideoOps.View> mVideoView;

	/**
	 * The GenericAsyncTask used to expand an Video in a background thread via the Video web service.
	 */
	private GenericAsyncTask<Void, Void, Cursor, VideoOps> mAsyncTask;

	/**
	 * VideoDataMediator mediates the communication between Video Service and local storage on the Android device.
	 */
	VideoDataMediator mVideoMediator;

	/**
	 * The Adapter that is needed by ListView to show the list of Videos.
	 */
	private SimpleCursorAdapter mAdapter;

	private ContentResolver mCr;

	/**
	 * Default constructor that's needed by the GenericActivity framework.
	 */
	public VideoOps() {
	}

	/**
	 * Called after a runtime configuration change occurs to finish the initialisation steps.
	 */
	@Override
	public void onConfiguration(VideoOps.View view, boolean firstTimeIn) {
		final String time = firstTimeIn ? "first time" : "second+ time";

		Log.d(TAG, "onConfiguration() called the " + time + " with view = " + view);

		// (Re)set the mVideoView WeakReference.
		mVideoView = new WeakReference<>(view);

		if (firstTimeIn) {
			// Create VideoDataMediator that will mediate the
			// communication between Server and Android Storage.
			mVideoMediator = new VideoDataMediator();

			mCr = mVideoView.get().getApplicationContext().getContentResolver();

			// Create a local instance of our custom Adapter for our
			// ListView.
			mAdapter = new SimpleCursorAdapter(mVideoView.get().getApplicationContext(), R.layout.video_list_item, null, // 
					new String[] { VideoEntry.COLUMN_TITLE }, //
					new int[] { R.id.tvVideoTitle }, //
					1);

			// Get the VideoList from Server. 
			getVideoList();
		}

		// Set the adapter to the ListView.
		mVideoView.get().setAdapter(mAdapter);
	}

	/**
	 * Start a service that Uploads the Video having given Id.
	 * 
	 * @param videoId
	 */
	public void uploadVideo(Uri videoUri) {
		// Sends an Intent command to the UploadVideoService.
		mVideoView.get().getApplicationContext().startService(UploadVideoService.makeIntent(mVideoView.get().getApplicationContext(), videoUri));
	}

	/**
	 * Gets the VideoList from Server by executing the AsyncTask to expand the acronym without blocking the caller.
	 */
	public void getVideoList() {
		mAsyncTask = new GenericAsyncTask<>(this);
		mAsyncTask.execute();
	}

	/**
	 * Retrieve the List of Videos by help of VideoDataMediator via a synchronous two-way method call, which runs in a
	 * background thread to avoid blocking the UI thread.
	 */
	@Override
	public Cursor doInBackground(Void... params) {
		List<Video> videos = mVideoMediator.getVideoList();
		if (videos == null) {
			return null;
		}

		populateContentProvider(videos);

		Cursor cursor = mCr.query(VideoEntry.CONTENT_URI, //
				new String[] { VideoEntry._ID, VideoEntry.COLUMN_TITLE }, //
				null, //
				null, //
				null);

		return cursor;
	}

	/**
	 * Display the results in the UI Thread.
	 */
	@Override
	public void onPostExecute(Cursor cursor) {
		displayVideoList(cursor);
	}

	/**
	 * Display the Videos in ListView.
	 * 
	 * @param videos
	 */
	public void displayVideoList(Cursor cursor) {
		if (cursor != null) {

			mAdapter.changeCursor(cursor);

			Utils.showToast(mVideoView.get().getActivityContext(), "Videos available from the Video Service");
		} else {
			Utils.showToast(mVideoView.get().getActivityContext(), "Please connect to the Video Service");

			// Close down the Activity.
			mVideoView.get().finish();
		}
	}

	private void populateContentProvider(List<Video> videos) {
		mCr.delete(VideoEntry.CONTENT_URI, null, null);
		List<ContentValues> contentValuesList = new ArrayList<>();
		for (Video video : videos) {
			ContentValues values = new ContentValues();
			values.put(VideoEntry._ID, video.getId());
			values.put(VideoEntry.COLUMN_TITLE, video.getTitle());
			values.put(VideoEntry.COLUMN_DURATION, video.getDuration());
			values.put(VideoEntry.COLUMN_DATA_URL, video.getDataUrl());
			values.put(VideoEntry.COLUMN_CONTENT_TYPE, video.getContentType());
			values.put(VideoEntry.COLUMN_AVARAGE_RATING, video.getAvarageRating());
			contentValuesList.add(values);
		}
		mCr.bulkInsert(VideoEntry.CONTENT_URI, contentValuesList.toArray(new ContentValues[contentValuesList.size()]));
	}
}
