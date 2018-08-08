package main;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;

import tool.UI;
import type.ImageData;
import type.MatchInfo;
import utilities.CameraModel;
import utilities.Reconstruction;

public class Main {

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	private static final int SKIP_FRAME_NUMBER = 30;
	private static final String VIDEO_FILE_NAME = "CVdata\\sfm\\sfm_1.mp4";
	private static final String IMG_LIST_FILE_NAME = "CVdata\\sfm\\imglist.txt";
	private static final String CALIB_LIST_FILE_NAME = "CVdata\\zhang_calib\\calibdata.txt";

	public static void main(String[] args) {
		Mat lastImage = new Mat();
		List<Mat> imageList = new ArrayList<Mat>();
		List<ImageData> imageDataList = new ArrayList<ImageData>();

		// ����Ƶ��ͼ���б��ȡͼ��
		UI.getMatListFromVideo(VIDEO_FILE_NAME, SKIP_FRAME_NUMBER, imageList);
		lastImage = imageList.get(imageList.size() - 1);
		// UI.getMatListFromImgList(IMG_LIST_FILE_NAME, imageList, lastImage);

		// չʾ��ȡ������ͼƬ
		// for (Mat img : imageList) new ImageUI(img,"images").imshow().waitKey(0);
		// new ImageUI(lastImage,"last one").imshow().waitKey(0);

		// ����궨
		CameraModel cm = new CameraModel(CALIB_LIST_FILE_NAME);

		// ��ά�ؽ�
		Reconstruction r = new Reconstruction(cm.getCameraMatrix());
		Reconstruction.extractFeatures(imageList, imageDataList);
		MatchInfo m = Reconstruction.matchFeatures(imageDataList.get(0), imageDataList.get(1));
		Mat cloud = r.InitPointCloud(imageDataList.get(0), imageDataList.get(1), m.getMatches(), imageList.get(1));
		
		System.out.println(cloud.dump());

	}

}