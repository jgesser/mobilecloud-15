package vandy.mooc.presenter;

import java.lang.ref.WeakReference;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;
import vandy.mooc.common.ConfigurableOps;
import vandy.mooc.common.ContextView;
import vandy.mooc.common.GenericAsyncTask;
import vandy.mooc.common.GenericAsyncTaskOps;
import vandy.mooc.common.Utils;
import vandy.mooc.model.mediator.VideoDataMediator;
import vandy.mooc.model.mediator.webdata.Video;
import vandy.mooc.model.provider.VideoContract.VideoEntry;
import vandy.mooc.model.services.DownloadVideoService;
import vandy.mooc.utils.VideoStorageUtils;

/**
 * Provides all the Video-related operations. It implements ConfigurableOps so it can be created/managed by the
 * GenericActivity framework. It extends GenericAsyncTaskOps so its doInBackground() method runs in a background task.
 * It plays the role of the "Abstraction" in Bridge pattern and the role of the "Presenter" in the Model-View-Presenter
 * pattern.
 */
public class VideoViewOps implements GenericAsyncTaskOps<Void, Void, Video>, ConfigurableOps<VideoViewOps.View> {
	/**
	 * Debugging tag used by the Android logger.
	 */
	private static final String TAG = VideoViewOps.class.getSimpleName();

	/**
	 * This interface defines the minimum interface needed by the VideoOps class in the "Presenter" layer to interact
	 * with the VideoListActivity in the "View" layer.
	 */
	public interface View extends ContextView {

		void finish();

		long getVideoId();

		void displayVideo(Video video);

	}

	/**
	 * Used to enable garbage collection.
	 */
	private WeakReference<VideoViewOps.View> mVideoView;

	/**
	 * The GenericAsyncTask used to expand an Video in a background thread via the Video web service.
	 */
	private GenericAsyncTask<Void, Void, Video, VideoViewOps> mAsyncTask;

	/**
	 * VideoDataMediator mediates the communication between Video Service and local storage on the Android device.
	 */
	VideoDataMediator mVideoMediator;

	private ContentResolver mCr;

	private Video video;

	/**
	 * Default constructor that's needed by the GenericActivity framework.
	 */
	public VideoViewOps() {
	}

	/**
	 * Called after a runtime configuration change occurs to finish the initialisation steps.
	 */
	@Override
	public void onConfiguration(VideoViewOps.View view, boolean firstTimeIn) {
		final String time = firstTimeIn ? "first time" : "second+ time";

		Log.d(TAG, "onConfiguration() called the " + time + " with view = " + view);

		// (Re)set the mVideoView WeakReference.
		mVideoView = new WeakReference<>(view);

		if (firstTimeIn) {
			// Create VideoDataMediator that will mediate the
			// communication between Server and Android Storage.
			mVideoMediator = new VideoDataMediator();

			mCr = mVideoView.get().getApplicationContext().getContentResolver();

			// Get the VideoList from Server. 
			loadVideo();
		}

		// Set the adapter to the ListView.
		//		mVideoView.get().setAdapter(mAdapter);
	}

	/**
	 * Start a service that Uploads the Video having given Id.
	 * 
	 * @param videoId
	 */
	public void downloadVideo() {
		// Sends an Intent command to the UploadVideoService.
		mVideoView.get().getApplicationContext().startService(DownloadVideoService.makeIntent(mVideoView.get().getApplicationContext(), video.getId(), video.getTitle()));
	}

	/**
	 * Gets the VideoList from Server by executing the AsyncTask to expand the acronym without blocking the caller.
	 */
	public void loadVideo() {
		mAsyncTask = new GenericAsyncTask<>(this);
		mAsyncTask.execute();
	}

	/**
	 * Retrieve the List of Videos by help of VideoDataMediator via a synchronous two-way method call, which runs in a
	 * background thread to avoid blocking the UI thread.
	 */
	@Override
	public Video doInBackground(Void... params) {
		long videoId = mVideoView.get().getVideoId();

		Cursor cursor = mCr.query(VideoEntry.CONTENT_URI, //
				new String[] { VideoEntry._ID, VideoEntry.COLUMN_TITLE, VideoEntry.COLUMN_DATA_URL, VideoEntry.COLUMN_AVARAGE_RATING }, //
				VideoEntry._ID + "=?", //
				new String[] { String.valueOf(videoId) }, //
				null);

		if (!cursor.moveToFirst()) {
			return null;
		}

		Video video = new Video();
		video.setId(cursor.getLong(0));
		video.setTitle(cursor.getString(1));
		video.setDataUrl(cursor.getString(2));
		video.setAvarageRating(cursor.getFloat(3));

		return video;
	}

	/**
	 * Display the results in the UI Thread.
	 */
	@Override
	public void onPostExecute(Video video) {
		displayVideo(video);
	}

	/**
	 * Display the Videos in ListView.
	 * 
	 * @param video
	 */
	private void displayVideo(Video video) {
		this.video = video;

		if (video != null) {

			mVideoView.get().displayVideo(video);
		} else {
			Utils.showToast(mVideoView.get().getActivityContext(), "Could not load video details");

			// Close down the Activity.
			mVideoView.get().finish();
		}
	}

	public void playVideo() {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(VideoStorageUtils.getDownloadedVideoUri(video.getId()), "video/*");
		mVideoView.get().getActivityContext().startActivity(intent);
	}

	public void updateRating(float rating) {
		new AsyncTask<Float, Void, Video>() {
			@Override
			protected Video doInBackground(Float... rating) {

				Video result = mVideoMediator.rateVideo(video.getId(), rating[0].intValue());

				ContentValues values = new ContentValues();
				values.put(VideoEntry.COLUMN_AVARAGE_RATING, result.getAvarageRating());
				mCr.update(VideoEntry.CONTENT_URI, values, VideoEntry._ID + "=?", new String[] { String.valueOf(result.getId()) });

				return result;
			}

			@Override
			protected void onPostExecute(Video result) {
				video = result;
				Utils.showToast(mVideoView.get().getActivityContext(), "Avarage rating updated");
				mVideoView.get().displayVideo(video);
			}
		}.execute(rating);

	}
}
