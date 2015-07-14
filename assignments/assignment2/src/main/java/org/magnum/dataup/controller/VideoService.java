package org.magnum.dataup.controller;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.magnum.dataup.VideoFileManager;
import org.magnum.dataup.VideoSvcApi;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class VideoService {

	private static final AtomicLong idGenerator = new AtomicLong();

	private static final Map<Long, Video> videos = new ConcurrentHashMap<>();

	@RequestMapping(value = VideoSvcApi.VIDEO_SVC_PATH, method = RequestMethod.GET)
	public @ResponseBody Collection<Video> getVideoList() {
		return videos.values();
	}

	@RequestMapping(value = VideoSvcApi.VIDEO_SVC_PATH, method = RequestMethod.POST)
	public @ResponseBody Video addVideo(@RequestBody Video video) {
		long id = idGenerator.incrementAndGet();
		videos.put(id, video);

		video.setId(id);
		video.setDataUrl(getDataUrl(id));

		return video;
	}

	@RequestMapping(value = VideoSvcApi.VIDEO_DATA_PATH, method = RequestMethod.POST)
	public @ResponseBody VideoStatus setVideoData(@PathVariable("id") Long id, @RequestPart(VideoSvcApi.DATA_PARAMETER) MultipartFile videoData, HttpServletResponse response) throws IOException {
		Video video = videos.get(id);
		if (video == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return null;
		}
		VideoFileManager.get().saveVideoData(video, videoData.getInputStream());
		return new VideoStatus(VideoStatus.VideoState.READY);
	}

	@RequestMapping(value = VideoSvcApi.VIDEO_DATA_PATH, method = RequestMethod.GET)
	public void getData(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
		Video video = videos.get(id);
		if (video == null || !VideoFileManager.get().hasVideoData(video)) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		VideoFileManager.get().copyVideoData(video, response.getOutputStream());
	}

	private String getDataUrl(long videoId) {
		return getUrlBaseForLocalServer() + VideoSvcApi.VIDEO_DATA_PATH.replace("{id}", String.valueOf(videoId));
	}

	private String getUrlBaseForLocalServer() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
		String base = "http://" + request.getServerName() + (request.getServerPort() != 80 ? ":" + request.getServerPort() : "");
		return base;
	}
}
