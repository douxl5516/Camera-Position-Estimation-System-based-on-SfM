package utilities;

import static org.opencv.calib3d.Calib3d.Rodrigues;
import static org.opencv.calib3d.Calib3d.findEssentialMat;
import static org.opencv.calib3d.Calib3d.solvePnPRansac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;


import tool.Format;
import type.ImageData;
import type.MatchInfo;

public class Reconstruction {

	private Mat cameraMat = new Mat(3, 3, CvType.CV_64F);	//����ڲ�
	private Mat pointCloud;
	private Mat color;
	private ArrayList<int[]> correspondence_idx = new ArrayList<>();
	private Mat LastP; // ���һ��ͼ�����ξ���
	private final float scale = 1 / 256;

	public Reconstruction(Mat cameraMat) {
		this.cameraMat = cameraMat;
	}

	/**
	 * ��������ȡ����
	 * 
	 * @param imgList     ����Mat���б�������ÿһ��ͼƬ������������ȡ
	 * @param imgDataList �����ȡ���ݵ��б�����ͼƬ�������㡢�����������Ӻ���ɫ
	 */
	public static void extractFeatures(List<Mat> imgList, List<ImageData> imgDataList) {
		for (int i = 0; i < imgList.size(); i++) {
			ImageData temp = detectFeature(imgList.get(i));
			if (temp.getKeyPoint().height() < 10) {// ���һ��ͼƬ��⵽����������С��10��������
				continue;
			}
			imgDataList.add(temp);
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
		Mat F = Calib3d.findHomography(kp1, kp2, Calib3d.FM_RANSAC, 3, inliner, 30, 0.99); // ������inliner��ͼƬ�ϵı任����
//		Mat F = Calib3d.findFundamentalMat(kp1, kp2, Calib3d.FM_RANSAC, 3, 0.99, inliner); // ������inliner�ǻ�������
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
	 * ��ʼ���ռ����
	 * 
	 * @param left  ��һ��ͼ��������Ϣ
	 * @param right �ڶ���ͼ��������Ϣ
	 * @param gm    ƥ����DMatch�б�
	 * @param img   �ṩ������ɫ��ͼ��
	 * @return ���ؿռ����
	 */
	public Mat InitPointCloud(ImageData left, ImageData right, MatOfDMatch gm, Mat img) {
		color = new Mat(gm.toList().size(), 1, CvType.CV_32FC3);
		MatOfKeyPoint leftPoint = left.getKeyPoint(); // ��ͼ�ؼ����б�
		MatOfKeyPoint rightPoint = right.getKeyPoint(); // ��ͼ�ؼ����б�
		Mat em; // EssentialMat�����ʾ�����ƽ����t�����ת����R�Ľ��
		Mat rot2 = new Mat(3, 3, CvType.CV_64F); // ��ת����
		Mat t2 = new Mat(3, 1, CvType.CV_64F); // ƽ�ƾ���

		LinkedList<Point> ptlist1 = new LinkedList<>();
		LinkedList<Point> ptlist2 = new LinkedList<>();
		MatOfPoint2f kp1 = new MatOfPoint2f();
		MatOfPoint2f kp2 = new MatOfPoint2f();
		int[] left_idx = new int[leftPoint.height()];
		int[] right_idx = new int[rightPoint.height()];
		Arrays.fill(left_idx, -1);
		Arrays.fill(right_idx, -1);

		for (int i = 0; i < gm.toList().size(); i++) {
			ptlist1.addLast(leftPoint.toList().get(gm.toList().get(i).queryIdx).pt); // ��i��ƥ�������ͼ�еĵ�����
			ptlist2.addLast(rightPoint.toList().get(gm.toList().get(i).trainIdx).pt); // ��i��ƥ�������ͼ�еĵ�����
			left_idx[gm.toList().get(i).queryIdx] = i; // ��ͼ�������Ӧ��ƥ��������
			right_idx[gm.toList().get(i).trainIdx] = i; // ��ͼ�������Ӧ��ƥ��������

			// ȡ��i��ƥ�������ͼ�ĵ�������Ϊ��ɫ��ȡֵ
			Point pt = rightPoint.toList().get(gm.toList().get(i).trainIdx).pt;
			double[] tmp = img.get((int) pt.y, (int) pt.x);
			tmp[0] *= scale;
			tmp[1] *= scale;
			tmp[2] *= scale;
			color.put(i, 0, tmp);
		}
		correspondence_idx.add(left_idx);
		correspondence_idx.add(right_idx);
		kp1.fromList(ptlist1);
		kp2.fromList(ptlist2);
		em = Calib3d.findEssentialMat(kp1, kp2);
		Calib3d.recoverPose(em, kp1, kp2, rot2, t2);
		Mat rot1 = Mat.eye(3, 3, CvType.CV_64F);
		Mat t1 = Mat.zeros(3, 1, CvType.CV_64F);
		Mat P1 = computeProjMat(cameraMat, rot1, t1);
		Mat P2 = computeProjMat(cameraMat, rot2, t2);
		Mat pc_raw = new Mat();
		Calib3d.triangulatePoints(P1, P2, kp1, kp2, pc_raw);
		pointCloud = divideLast(pc_raw);
		LastP = P2.clone();
		return pointCloud.clone();
	}

	public Mat computeProjMat(Mat K, Mat R, Mat T) {
		Mat Proj = new Mat(3, 4, CvType.CV_64F);
		Mat RT = new Mat();
		RT.push_back(R.t());
		RT.push_back(T.t());
		Core.gemm(K, RT.t(), 1, new Mat(), 0, Proj, 0);
		double test[] = new double[12];
		Proj.get(0, 0, test);
		return Proj;
	}

	public Mat divideLast(Mat raw) {
		Mat pc = new Mat();
		for (int i = 0; i < raw.cols(); i++) {
			Mat col = new Mat(4, 1, CvType.CV_32F);
			Core.divide(raw.col(i), new Scalar(raw.col(i).get(3, 0)), col);
			pc.push_back(col.t());
		}
		return pc.colRange(0, 3);
	}

	public Mat addImage(ImageData left, ImageData right, MatOfDMatch gm, Mat img) {
		MatOfPoint3f pc3f = Format.Mat2MatOfPoint3f(pointCloud);
		MatOfKeyPoint leftPoint = left.getKeyPoint();
		MatOfKeyPoint rightPoint = right.getKeyPoint();
		LinkedList<Point3> pclist = new LinkedList<>();
		LinkedList<Point> right_inPC = new LinkedList<>();
		LinkedList<Point> leftlist = new LinkedList<>();
		LinkedList<Point> rightist = new LinkedList<>();
		MatOfPoint3f pc = new MatOfPoint3f();
		MatOfPoint2f kp1 = new MatOfPoint2f();
		MatOfPoint2f kp2 = new MatOfPoint2f();
		int count = pointCloud.height();
		int[] left_idx = correspondence_idx.get(correspondence_idx.size() - 1);
		int[] right_idx = new int[rightPoint.height()];
		Arrays.fill(right_idx, -1);
		for (int i = 0; i < gm.toList().size(); i++) {
			if (left_idx[gm.toList().get(i).queryIdx] >= 0) {
				pclist.addLast(pc3f.toList().get(left_idx[gm.toList().get(i).queryIdx]));
				right_inPC.addLast(rightPoint.toList().get(gm.toList().get(i).trainIdx).pt);
				right_idx[gm.toList().get(i).trainIdx] = left_idx[gm.toList().get(i).queryIdx];
			} else {
				leftlist.addLast(leftPoint.toList().get(gm.toList().get(i).queryIdx).pt);
				rightist.addLast(rightPoint.toList().get(gm.toList().get(i).trainIdx).pt);
				left_idx[gm.toList().get(i).queryIdx] = count;
				right_idx[gm.toList().get(i).trainIdx] = count;
				int y = (int) rightPoint.toList().get(gm.toList().get(i).trainIdx).pt.y;
				int x = (int) rightPoint.toList().get(gm.toList().get(i).trainIdx).pt.x;
				double[] tmp = img.get(y, x);
				tmp[0] *= scale;
				tmp[1] *= scale;
				tmp[2] *= scale;
				Mat dummy = new Mat(1, 1, CvType.CV_32FC4);
				color.push_back(dummy);
				count++;
			}
		}
		pc.fromList(pclist);
		kp2.fromList(right_inPC);
		Mat rotvec = new Mat(3, 1, CvType.CV_64F);
		Mat rot = new Mat(3, 3, CvType.CV_64F);
		Mat t = new Mat(3, 1, CvType.CV_64F);
		solvePnPRansac(pc, kp2, cameraMat, new MatOfDouble(), rotvec, t);
		kp1.fromList(leftlist);
		kp2.fromList(rightist);
		Rodrigues(rotvec, rot);
		Mat P = computeProjMat(cameraMat, rot, t);
		Mat pc_raw = new Mat();
		Calib3d.triangulatePoints(LastP, P, kp1, kp2, pc_raw);
		Mat new_PC = divideLast(pc_raw);
		pointCloud.push_back(new_PC);
		LastP = P.clone();
		return pointCloud.clone();
	}

	/**
	 * �������⺯��������Mat��ʹ��SIFT�㷨��������㲢����������������
	 * 
	 * @param image ����Ĵ�����ͼƬ
	 * @return ImageData ������������������������������Լ���ɫ��Ϣ
	 */
	private static ImageData detectFeature(Mat image) {
		int channels = image.channels();
		Mat img = new Mat(image.height(), image.width(), CvType.CV_8UC3);
		FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
		DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
		MatOfKeyPoint keypoints = new MatOfKeyPoint();
		Mat descriptors = new Mat();
		if (channels == 3) {
			Imgproc.cvtColor(image, img, Imgproc.COLOR_RGB2BGR, 3); // ת��ΪBGR��ɫģʽ��Ϊ��Ч�ʿ���
		}
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

	/**
	 * ��������ͼƬ֮�����ת�����ƽ�ƾ���
	 * 
	 * @param K    ����ڲξ���
	 * @param p1   �ؼ����б�1
	 * @param p2   �ؼ����б�2
	 * @param R    ����������ת����
	 * @param T    ��������ƽ�ƾ���
	 * @param mask mask�д�����ĵ����ƥ��㣬���������ʧ���
	 * @return ����booleanֵ���Ƿ���ɹ�
	 */
	private boolean find_transform(Mat K, MatOfPoint p1, MatOfPoint p2, Mat R, Mat T, Mat mask) {
		// �����ڲξ����ȡ����Ľ���͹�������
		double focal_length = 0.5 * (K.get(0, 0)[0] + K.get(1, 1)[0]);
		Point principle_point = new Point(K.get(0, 2)[0], K.get(1, 2)[0]);
		// ����ƥ�����ȡ��������ʹ��RANSAC����һ���ų�ʧ���
		Mat E = Calib3d.findEssentialMat(p1, p2, focal_length, principle_point, Calib3d.FM_RANSAC, 0.999, 1.0, mask);
		if (E.empty()) {
			return false;
		}
		int feasible_count = Core.countNonZero(mask);
		System.out.println(feasible_count + " -in- " + p1.height());
		// ����RANSAC���ԣ�outlier��������50%ʱ������ǲ��ɿ���
		if (feasible_count <= 15 || (feasible_count / p1.height()) < 0.6)
			return false;
		// �ֽⱾ�����󣬻�ȡ��Ա任
		int pass_count = Calib3d.recoverPose(E, p1, p2, R, T, focal_length, principle_point, mask);
		// ͬʱλ���������ǰ���ĵ������Ҫ�㹻��
		if (((double) pass_count) / feasible_count < 0.7) {
			return false;
		}
		return true;
	}
}
