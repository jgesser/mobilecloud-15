package vandy.mooc.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.RatingBar.OnRatingBarChangeListener;
import android.widget.TextView;
import vandy.mooc.R;
import vandy.mooc.common.GenericActivity;
import vandy.mooc.model.mediator.webdata.Video;
import vandy.mooc.model.services.DownloadVideoService;
import vandy.mooc.presenter.VideoViewOps;
import vandy.mooc.utils.VideoStorageUtils;

/**
 * This Activity can be used upload a selected video to a Video Service and also displays a list of videos available at
 * the Video Service. The user can record a video or get a video from gallery and upload it. It implements
 * OnVideoSelectedListener that will handle callbacks from the UploadVideoDialog Fragment. It extends GenericActivity
 * that provides a framework for automatically handling runtime configuration changes of an VideoOps object, which plays
 * the role of the "Presenter" in the MVP pattern. The VideoOps.View interface is used to minimize dependencies between
 * the View and Presenter layers.
 */
public class VideoViewActivity extends GenericActivity<VideoViewOps.View, VideoViewOps>implements VideoViewOps.View {

	private static final String VIDEO_ID = "VIDEO_ID";

	public static Intent makeIntent(Context context, long id) {
		Intent ret = new Intent(context, VideoViewActivity.class);
		ret.putExtra(VIDEO_ID, id);
		return ret;
	}

	/**
	 * The Broadcast Receiver that registers itself to receive result from UploadVideoService.
	 */
	private class DownloadResultReceiver extends BroadcastReceiver {
		/**
		 * Hook method that's dispatched when the UploadService has uploaded the Video.
		 */
		@Override
		public void onReceive(Context context, Intent intent) {
			// Starts an AsyncTask to get fresh Video list from the
			// Video Service.
			getOps().loadVideo();
		}
	}

	private DownloadResultReceiver downloadResultReceiver;
	private Button downloadButton;
	private Button playButton;
	private TextView title;
	private RatingBar rating;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Initialize the default layout.
		setContentView(R.layout.video_view);

		downloadResultReceiver = new DownloadResultReceiver();

		playButton = (Button) findViewById(R.id.videoPlayButton);
		downloadButton = (Button) findViewById(R.id.videoDownloadButton);
		title = (TextView) findViewById(R.id.videoTitle);
		rating = (RatingBar) findViewById(R.id.videoRatingBar);

		downloadButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				getOps().downloadVideo();
			}
		});

		playButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				getOps().playVideo();
			}
		});

		rating.setOnRatingBarChangeListener(new OnRatingBarChangeListener() {

			@Override
			public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
				if (fromUser) {
					getOps().updateRating(rating);
				}
			}
		});

		// Invoke the special onCreate() method in GenericActivity,
		// passing in the VideoOps class to instantiate/manage and
		// "this" to provide VideoOps with the VideoOps.View instance.
		super.onCreate(savedInstanceState, VideoViewOps.class, this);
	}

	/**
	 * Hook method that is called when user resumes activity from paused state, onPause().
	 */
	@Override
	protected void onResume() {
		// Call up to the superclass.
		super.onResume();

		// Register BroadcastReceiver that receives result from
		// UploadVideoService when a video upload completes.
		registerReceiver();
	}

	/**
	 * Hook method that gives a final chance to release resources and stop spawned threads. onDestroy() may not always
	 * be called-when system kills hosting process.
	 */
	@Override
	protected void onPause() {
		// Call onPause() in superclass.
		super.onPause();

		// Unregister BroadcastReceiver.
		LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadResultReceiver);
	}

	/**
	 * Register a BroadcastReceiver that receives a result from the UploadVideoService when a video upload completes.
	 */
	private void registerReceiver() {

		// Create an Intent filter that handles Intents from the
		// UploadVideoService.
		IntentFilter intentFilter = new IntentFilter(DownloadVideoService.ACTION_DOWNLOAD_SERVICE_RESPONSE);
		intentFilter.addCategory(Intent.CATEGORY_DEFAULT);

		// Register the BroadcastReceiver.
		LocalBroadcastManager.getInstance(this).registerReceiver(downloadResultReceiver, intentFilter);
	}

	/**
	 * Finishes this Activity.
	 */
	@Override
	public void finish() {
		super.finish();
	}

	@Override
	public long getVideoId() {
		return getIntent().getLongExtra(VIDEO_ID, 0);
	}

	@Override
	public void displayVideo(Video video) {
		title.setText(video.getTitle());
		rating.setRating(video.getAvarageRating());

		boolean videoDownloaded = VideoStorageUtils.isVideoDownloaded(video.getId());
		playButton.setVisibility(videoDownloaded ? View.VISIBLE : View.INVISIBLE);
		downloadButton.setVisibility(videoDownloaded || video.getDataUrl() == null ? View.INVISIBLE : View.VISIBLE);
	}

}
