package main;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import tool.ImageUI;
import tool.UI;
import type.ImageData;
import type.MatchInfo;
import utilities.CameraModel;
import utilities.Features;
import utilities.Reconstruction;

public class Main {

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	private static final int SKIP_FRAME_NUMBER = 30;
	private static final String VIDEO_FILE_NAME = "CVdata\\sfm\\sfm_1.mp4";
	private static final String IMG_LIST_FILE_NAME = "CVdata\\sift\\imglist.txt";
	private static final String CALIB_LIST_FILE_NAME = "CVdata\\zhang_calib\\calibdata.txt";

	public static void main(String[] args) {
		Mat lastImage = new Mat();
		List<Mat> imageList = new LinkedList<Mat>();
		List<ImageData> imageDataList = new LinkedList<ImageData>();
		List<MatchInfo> matchesList = new LinkedList<MatchInfo>();

		// ����Ƶ��ͼ���б��ȡͼ��
//		UI.getMatListFromVideo(VIDEO_FILE_NAME, SKIP_FRAME_NUMBER, imageList);	
		UI.getMatListFromImgList(IMG_LIST_FILE_NAME, imageList);
		lastImage = imageList.get(imageList.size() - 1);
		// չʾ��ȡ������ͼƬ
//		 for (Mat img : imageList) new ImageUI(img,"images").imshow().waitKey(0);
//		 new ImageUI(lastImage,"last one").imshow().waitKey(0);

		// ����궨
//		CameraModel cm = new CameraModel(CALIB_LIST_FILE_NAME);
		CameraModel cm = new CameraModel(
				new MatOfDouble(2759.48, 0, 1520.69, 0, 2764.16, 1006.81, 0, 0, 1).reshape(1, 3));

		// ��������
		Features.extractFeatures(imageList, imageDataList);

		// �洢������������
//		for(int i=0;i<imageDataList.size();i++) {
//			Mat out=new Mat();
//			Features2d.drawKeypoints(imageList.get(i), imageDataList.get(i).getKeyPoint(), out);
//			Imgcodecs.imwrite("output\\result_of_features_"+i+".jpg", out);
//		}

		// ��ά�ؽ�
		Reconstruction r = new Reconstruction(cm.getCameraMatrix(), imageList, imageDataList, matchesList);

	}
}