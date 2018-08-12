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
import org.opencv.features2d.*;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import type.ImageData;

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
			System.out.println("��"+(i+1)+"��ͼ���⵽"+temp.getKeyPoint().height()+"��������");
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
	public static void matchFeatures(List<ImageData> imageDataForAll,List<MatOfDMatch> matchesForAll) {
		matchesForAll.clear();
		for (int i = 0; i < imageDataForAll.size() - 1; i++){
			MatOfDMatch matches=null;
			matches=matchFeatures(imageDataForAll.get(i), imageDataForAll.get(i+1));
			System.out.println("��"+(i+1)+"���"+(i+2)+"��ͼ�����"+matches.height()+"��ƥ����");
			matchesForAll.add(matches);
		}
	}
	
	/**
	 * ������ƥ�亯��
	 * 
	 * @param query �����һ�Ŵ�ƥ��ͼƬ����Ϣ
	 * @param train �����һ�Ŵ�ƥ��ͼƬ����Ϣ
	 * @return ��������ͼ���ƥ����MatOfDMatch
	 */
	public static MatOfDMatch matchFeatures(ImageData query, ImageData train) {
		DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		List<MatOfDMatch> knnmatches = new LinkedList<MatOfDMatch>();
		List<DMatch> matchesList = new LinkedList<DMatch>();
		matcher.knnMatch(query.getDescriptors(), train.getDescriptors(), knnmatches,2);
		
		//��ȡ����Ratio Test����Сƥ��ľ���
		double min_dist =Double.MAX_VALUE;
		for (int r = 0; r < knnmatches.size(); r++){
			//Ratio Test
			if (knnmatches.get(r).toArray()[0].distance > 0.6*knnmatches.get(r).toArray()[1].distance)
				continue;
			double dist = knnmatches.get(r).toArray()[0].distance;
			if (dist < min_dist) min_dist = dist;
		}
		matchesList.clear();
		for (int r = 0; r < knnmatches.size(); r++)
		{
			//�ų�������Ratio Test�ĵ��ƥ��������ĵ�
			if (
				knnmatches.get(r).toArray()[0].distance > 0.6*knnmatches.get(r).toArray()[1].distance ||
				knnmatches.get(r).toArray()[0].distance > 5 * (min_dist>10.0f?min_dist:10.0f)
				)
				continue;
			//����ƥ���
			matchesList.add(knnmatches.get(r).toArray()[0]);
		}
		MatOfDMatch goodMatch=new MatOfDMatch();
		goodMatch.fromList(matchesList);
		return goodMatch;
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
		FeatureDetector detector = FeatureDetector.create(FeatureDetector.AKAZE);
		DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.AKAZE);
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
				throw new Exception("�������������ͼ����RGB����");
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
