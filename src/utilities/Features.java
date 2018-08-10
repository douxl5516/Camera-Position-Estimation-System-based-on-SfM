package utilities;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import type.ImageData;
import type.MatchInfo;

public class Features {
	/**
	 * ��������ȡ����
	 * 
	 * @param imgList     ����Mat���б�������ÿһ��ͼƬ������������ȡ
	 * @param imgDataList �����ȡ���ݵ��б�����ͼƬ�������㡢�����������Ӻ���ɫ
	 */
	public static void extractFeatures(List<Mat> imageList, List<ImageData> imageDataList) {
		for (int i = 0; i < imageList.size(); i++) {
			ImageData temp = detectFeature(imageList.get(i));
			if (temp.getKeyPoint().height() < 10) {// ���һ��ͼƬ��⵽����������С��10��������
				imageList.remove(i);
				continue;
			}
			imageDataList.add(temp);
		}
	}

	/**
	 * ������ƥ�亯��
	 * 
	 * @param imageDataForAll ������ͼƬ���������������ڵ��б�
	 * @param matchesForAll	�������ƥ����б�
	 */
	public static void matchFeatures(List<ImageData> imageDataForAll,List<MatchInfo> matchesForAll) {
		matchesForAll.clear();
		for (int i = 0; i < imageDataForAll.size() - 1; i++){
			MatchInfo matches=null;
			matches=matchFeatures(imageDataForAll.get(i), imageDataForAll.get(i+1));
			matchesForAll.add(matches);
		}
	}
	
	/**
	 * ������ƥ�亯��
	 * 
	 * @param query �����һ�Ŵ�ƥ��ͼƬ����Ϣ
	 * @param train �����һ�Ŵ�ƥ��ͼƬ����Ϣ
	 * @return ��������ͼ���ƥ����MatchInfo
	 */
	public static MatchInfo matchFeatures(ImageData query, ImageData train) {
		DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		MatOfDMatch matches = new MatOfDMatch();
		matcher.match(query.getDescriptors(), train.getDescriptors(), matches);
		
		List<DMatch> matchesList = matches.toList();
		List<KeyPoint> kpList1 = query.getKeyPoint().toList();
		List<KeyPoint> kpList2 = train.getKeyPoint().toList();
		
		LinkedList<Point> points1 = new LinkedList<>();
		LinkedList<Point> points2 = new LinkedList<>();
		for (int i = 0; i < matchesList.size(); i++) {
			points1.addLast(kpList1.get(matchesList.get(i).queryIdx).pt);
			points2.addLast(kpList2.get(matchesList.get(i).trainIdx).pt);
		}
		MatOfPoint2f kp1 = new MatOfPoint2f();
		MatOfPoint2f kp2 = new MatOfPoint2f();
		kp1.fromList(points1);
		kp2.fromList(points2);
		
		Mat inliner = new Mat();
//		Mat F = Calib3d.findHomography(kp1, kp2, Calib3d.FM_RANSAC, 3, inliner, 30, 0.99); // ������inliner��ͼƬ�ϵı任����
		Mat F = Calib3d.findFundamentalMat(kp1, kp2, Calib3d.FM_RANSAC, 3, 0.99, inliner); // ������inliner�ǻ�������
		System.out.println(F.dump());
		List<Byte> isInliner = new ArrayList<>();
		Converters.Mat_to_vector_uchar(inliner, isInliner);
		LinkedList<DMatch> good_matches = new LinkedList<>();
		MatOfDMatch gm = new MatOfDMatch();
		for (int i = 0; i < isInliner.size(); i++) {
			if (isInliner.get(i) != 0) {
				good_matches.addLast(matchesList.get(i));
			}
		}
		gm.fromList(good_matches);
		return MatchInfo.newInstance(gm, F);
	}

	/**
	 * �������⺯��������Mat��ʹ��SIFT�㷨��������㲢����������������
	 * 
	 * @param image ����Ĵ�����ͼƬ
	 * @return ImageData ������������������������������Լ���ɫ��Ϣ
	 */
	private static ImageData detectFeature(Mat image) {
		try {
			int channels = image.channels();
			if (channels != 3) {
				throw new Exception("ͼ��ΪRGB");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		Mat img = new Mat(image.height(), image.width(), CvType.CV_8UC3); // ���ڴ洢ת��ΪBGR���ͼ��
		FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
		DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
		MatOfKeyPoint keypoints = new MatOfKeyPoint();
		Mat descriptors = new Mat();
		Imgproc.cvtColor(image, img, Imgproc.COLOR_RGB2BGR, 3); // ת��ΪBGR��ɫģʽ��Ϊ��Ч�ʿ���
		detector.detect(img, keypoints);
		extractor.compute(img, keypoints, descriptors);
		Mat color = extractKeypointColor(keypoints, image);
		return ImageData.newInstance(keypoints, descriptors, color);
	}

	/**
	 * ��������ɫ��ȡ
	 * 
	 * @param keyPoint �������б�
	 * @param img      ԭͼ��
	 * @return ��������ɫ��Mat
	 */
	private static Mat extractKeypointColor(MatOfKeyPoint keyPoint, Mat img) {
		int channels = img.channels();
		Mat color = null;
		try {
			if (channels == 3) {
				color = new Mat(keyPoint.height(), 1, CvType.CV_32FC3);
			} else {
				throw new Exception("�������������ͼ����RGB��");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		float scale = 1.0f / 256.0f;
		for (int i = 0; i < keyPoint.toList().size(); i++) {
			int y = (int) keyPoint.toList().get(i).pt.y;
			int x = (int) keyPoint.toList().get(i).pt.x;
			double[] tmp = img.get(y, x);
			for (int j = 0; j < color.channels(); j++) {
				tmp[j] *= scale;
			}
			color.put(i, 0, tmp);
		}
		return color.clone();
	}

}
