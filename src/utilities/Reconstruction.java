package utilities;

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

import tool.Format;
import tool.UI;
import type.ImageData;
import type.MatchInfo;

public class Reconstruction {

	private Mat cameraMat = new Mat(3, 3, CvType.CV_64F); // ����ڲ�
	private Mat pointCloud;
	private Mat color;
	private Mat LastP; // ���һ��ͼ�����ξ���
	private ArrayList<int[]> correspondence_idx = new ArrayList<>();
	List<Mat> imageList;
	List<ImageData> imageDataList;
	List<MatchInfo> matchesList;
	private final float scale = 1 / 256;

	public Reconstruction(Mat cameraMat, List<Mat> imageList, List<ImageData> imageDataList,
			List<MatchInfo> matchesList) {
		this.cameraMat = cameraMat;
		this.imageList = imageList;
		this.imageDataList = imageDataList;
		this.matchesList = matchesList;
	}

	public void runSfM() {
		InitStructure(imageDataList.get(0), imageDataList.get(1), matchesList.get(0).getMatches(), imageList.get(1));
		System.out.println("����size:" + pointCloud.size());
		for (int i = 1; i < matchesList.size(); i++) {
			addImage(imageDataList.get(i), imageDataList.get(i + 1), matchesList.get(i).getMatches(),
					imageList.get(i + 1));
			System.out.println("����size:" + pointCloud.size());
		}
		UI.writePointCloud("output\\pointcloud.txt", pointCloud);
	}

	/**
	 * ��ʼ���ռ����
	 * 
	 * @param left  ��һ��ͼ��������Ϣ
	 * @param right �ڶ���ͼ��������Ϣ
	 * @param gm    ͼ�������е�һ�ź͵ڶ���ͼ��ƥ����DMatch�б�
	 * @param img   �ṩ������ɫ��ͼ��
	 * @return ���ؿռ����
	 */
	private Mat InitStructure(ImageData left, ImageData right, MatOfDMatch gm, Mat img) {
		color = new Mat();
		MatOfKeyPoint leftPoint = left.getKeyPoint(); // ԭ��ͼ�ؼ���mat
		MatOfKeyPoint rightPoint = right.getKeyPoint(); // ԭ��ͼ�ؼ���mat
		List<KeyPoint> leftPointList=leftPoint.toList(); // ԭ��ͼ�ؼ����б�
		List<KeyPoint> rightPointList=rightPoint.toList(); // ԭ��ͼ�ؼ����б�
		List<DMatch> matchesList=gm.toList();
		Mat rot1 = Mat.eye(3, 3, CvType.CV_64F); // ��ͼ�����ת����
		Mat t1 = Mat.zeros(3, 1, CvType.CV_64F); // ��ͼ���ƽ�ƾ���
		Mat rot2 = new Mat(3, 3, CvType.CV_64F); // ��ͼ�����ת����
		Mat t2 = new Mat(3, 1, CvType.CV_64F); // ��ͼ���ƽ�ƾ���
		Mat mask = new Mat();
		LinkedList<Point> ptlist1 = new LinkedList<>(); // ��ƥ������ͼ�������б�
		LinkedList<Point> ptlist2 = new LinkedList<>(); // ��ƥ������ͼ�������б�
		MatOfPoint2f kp1 = new MatOfPoint2f();
		MatOfPoint2f kp2 = new MatOfPoint2f();
		int[] left_idx = new int[leftPoint.height()];
		int[] right_idx = new int[rightPoint.height()];
		Arrays.fill(left_idx, -1);
		Arrays.fill(right_idx, -1);

		// ��ȡƥ���Բ���������
		for (int i = 0; i < matchesList.size(); i++) {
			DMatch match = matchesList.get(i);
			ptlist1.addLast(leftPointList.get(match.queryIdx).pt);
			ptlist2.addLast(rightPointList.get(match.trainIdx).pt);
		}
		kp1.fromList(ptlist1);
		kp2.fromList(ptlist2);
		Mat E = Calib3d.findEssentialMat(kp1, kp2, cameraMat, Calib3d.RANSAC, 0.999, 1.0, mask);
		Calib3d.recoverPose(E, kp1, kp2, cameraMat, rot2, t2, mask);
		maskoutPoints(ptlist1, mask);
		maskoutPoints(ptlist2, mask);
		kp1.release();
		kp2.release();
		kp1.fromList(ptlist1);
		kp2.fromList(ptlist1);
		Mat P1 = computeProjMat(cameraMat, rot1, t1);
		Mat P2 = computeProjMat(cameraMat, rot2, t2);
		left.setProj(P1.clone());
		right.setProj(P2.clone());
		Mat pc_raw = new Mat();
		Calib3d.triangulatePoints(P1, P2, kp1, kp2, pc_raw);
		pointCloud = divideLast(pc_raw);
		LastP = P2.clone();

		int idx = 0;
		for (int i = 0; i < matchesList.size(); i++) {
			if (mask.get(i, 0)[0] == 0)
				continue;
			DMatch match = matchesList.get(i);
			left_idx[match.queryIdx] = idx;
			right_idx[match.trainIdx] = idx;
			Point pt = rightPointList.get(match.trainIdx).pt;
			double[] tmp = img.get((int) pt.y, (int) pt.x);
			tmp[0] *= scale;
			tmp[1] *= scale;
			tmp[2] *= scale;
			Mat dummy = new Mat(1, 1, CvType.CV_32FC3);
			color.push_back(dummy);
			idx++;
		}
		correspondence_idx.add(left_idx);
		correspondence_idx.add(right_idx);
		return pointCloud.clone();
	}


	/**
	 * ����������һ��ͼƬ
	 * 
	 * @param left  ��ͼ�����Ϣ
	 * @param right ��ͼ�����Ϣ
	 * @param gm    ƥ����
	 * @param img   ȡ��ɫ��ͼ��
	 * @return �ؽ������ά����
	 */
	private Mat addImage(ImageData left, ImageData right, MatOfDMatch matches, Mat img) {
		MatOfPoint3f pc3f = Format.Mat2MatOfPoint3f(pointCloud); // ԭ�еĵ��Ƶ�Point3f
		MatOfKeyPoint leftPoint = left.getKeyPoint(); // ԭ����ͼ�ؼ���mat
		MatOfKeyPoint rightPoint = right.getKeyPoint(); // ԭ����ͼ�ؼ���mat
		List<KeyPoint> leftPointList = leftPoint.toList(); // ԭ����ͼ�ؼ����б�
		List<KeyPoint> rightPointList = rightPoint.toList(); // ԭ����ͼ�ؼ����б�
		List<DMatch> matchesList=matches.toList();
		LinkedList<Point3> objectPoints = new LinkedList<Point3>(); // maskƥ���Ŀռ���б�
		LinkedList<Point> imagePoints = new LinkedList<Point>(); // maskƥ������ͼ�Ĺؼ����б�
		MatOfPoint3f opMat = new MatOfPoint3f(); // maskƥ���Ŀռ��mat
		MatOfPoint2f ipMat = new MatOfPoint2f(); // maskƥ������ͼ�Ĺؼ���mat
		LinkedList<Point> ptlist1 = new LinkedList<>(); // ��ƥ������ͼ�������б�
		LinkedList<Point> ptlist2 = new LinkedList<>(); // ��ƥ������ͼ�������б�
		MatOfPoint2f kp1 = new MatOfPoint2f(); // ��ƥ������ͼ������mat
		MatOfPoint2f kp2 = new MatOfPoint2f(); // ��ƥ������ͼ������mat

		List<Point3> pointCloudList = pc3f.toList();// �����б�
		List<DMatch> matchList = matches.toList();// ƥ���б�
		int[] left_idx = correspondence_idx.get(correspondence_idx.size() - 1); // ���һ��ͼ���ƥ������
		int[] right_idx = new int[rightPoint.height()];
		Arrays.fill(right_idx, -1);

		// ��ȡ��i��ͼ����ƥ����Ӧ����ά�㣬�Լ��ڵ�i+1��ͼ���ж�Ӧ�����ص�
		for (int i = 0; i < matchList.size(); i++) {
			DMatch match = matchList.get(i);
			int cloudIdx = left_idx[match.queryIdx];
			if (cloudIdx < 0)
				continue;
			objectPoints.add(pointCloudList.get(cloudIdx));
			imagePoints.add(rightPointList.get(match.trainIdx).pt);
		}
		opMat.fromList(objectPoints);
		ipMat.fromList(imagePoints);

		Mat rotvec = new Mat(3, 1, CvType.CV_64F);
		Mat rot = new Mat(3, 3, CvType.CV_64F);
		Mat t = new Mat(3, 1, CvType.CV_64F);
		Calib3d.solvePnPRansac(opMat, ipMat, cameraMat, new MatOfDouble(), rotvec, t);
		Calib3d.Rodrigues(rotvec, rot);
		Mat P = computeProjMat(cameraMat, rot, t);
		right.setProj(P);

		// ��ȡƥ���Բ���������
		for (int i = 0; i < matchesList.size(); i++) {
			DMatch match = matchesList.get(i);
			ptlist1.addLast(leftPointList.get(match.queryIdx).pt);
			ptlist2.addLast(rightPointList.get(match.trainIdx).pt);
		}
		kp1.fromList(ptlist1);
		kp2.fromList(ptlist2);
		Mat nextStructure = new Mat();
		Calib3d.triangulatePoints(LastP, P, kp1, kp2, nextStructure);
		Mat last=divideLast(nextStructure);
		
		for(int i=0;i<matchesList.size();i++) {
			DMatch match=matchesList.get(i);
			if(left_idx[match.queryIdx]>=0) {
				//���õ��ڿռ����Ѿ����ڣ������ƥ����Ӧ�Ŀռ��Ӧ����ͬһ��������Ҫ��ͬ
				right_idx[match.trainIdx]=left_idx[match.queryIdx];
				continue;
			}
			//���õ��ڿռ����Ѿ����ڣ����õ���뵽�ṹ�У������ƥ���Ŀռ��������Ϊ�¼���ĵ������
			pointCloud.push_back(last.row(i));
			
			Point pt = rightPointList.get(match.trainIdx).pt;
			double[] tmp = img.get((int) pt.y, (int) pt.x);
			tmp[0] *= scale;
			tmp[1] *= scale;
			tmp[2] *= scale;
			Mat dummy = new Mat(1, 1, CvType.CV_32FC3);
			color.push_back(dummy);
			left_idx[match.queryIdx]=pointCloud.height()-1;
			right_idx[match.trainIdx]=pointCloud.height()-1;
		}
		correspondence_idx.remove(correspondence_idx.size()-1);
		correspondence_idx.add(left_idx);
		correspondence_idx.add(right_idx);
		LastP=P.clone();
		return pointCloud.clone();
	}

	/**
	 * �Ʋ����λ��
	 * 
	 * @param left    ���һ��ͼ��
	 * @param right   �����任���Ŀ��ͼ��
	 * @param matches ƥ���б�
	 * @param img     ����ȡ��ɫ��ͼ��
	 * @return �������ξ���
	 */
	public Mat reckon(ImageData left, ImageData right, MatOfDMatch matches, Mat img) {
		MatOfPoint3f pc3f = Format.Mat2MatOfPoint3f(pointCloud); // ԭ�еĵ��Ƶ�Point3f
		MatOfKeyPoint leftPoint = left.getKeyPoint(); // ԭ����ͼ�ؼ���mat
		MatOfKeyPoint rightPoint = right.getKeyPoint(); // ԭ����ͼ�ؼ���mat
		List<KeyPoint> leftPointList = leftPoint.toList(); // ԭ����ͼ�ؼ����б�
		List<KeyPoint> rightPointList = rightPoint.toList(); // ԭ����ͼ�ؼ����б�
		List<DMatch> matchesList=matches.toList();
		LinkedList<Point3> objectPoints = new LinkedList<Point3>(); // maskƥ���Ŀռ���б�
		LinkedList<Point> imagePoints = new LinkedList<Point>(); // maskƥ������ͼ�Ĺؼ����б�
		MatOfPoint3f opMat = new MatOfPoint3f(); // maskƥ���Ŀռ��mat
		MatOfPoint2f ipMat = new MatOfPoint2f(); // maskƥ������ͼ�Ĺؼ���mat
		LinkedList<Point> ptlist1 = new LinkedList<>(); // ��ƥ������ͼ�������б�
		LinkedList<Point> ptlist2 = new LinkedList<>(); // ��ƥ������ͼ�������б�
		MatOfPoint2f kp1 = new MatOfPoint2f(); // ��ƥ������ͼ������mat
		MatOfPoint2f kp2 = new MatOfPoint2f(); // ��ƥ������ͼ������mat

		List<Point3> pointCloudList = pc3f.toList();// �����б�
		List<DMatch> matchList = matches.toList();// ƥ���б�
		int[] left_idx = correspondence_idx.get(correspondence_idx.size() - 1); // ���һ��ͼ���ƥ������
		int[] right_idx = new int[rightPoint.height()];
		Arrays.fill(right_idx, -1);

		// ��ȡ��i��ͼ����ƥ����Ӧ����ά�㣬�Լ��ڵ�i+1��ͼ���ж�Ӧ�����ص�
		for (int i = 0; i < matchList.size(); i++) {
			DMatch match = matchList.get(i);
			int cloudIdx = left_idx[match.queryIdx];
			if (cloudIdx < 0)
				continue;
			objectPoints.add(pointCloudList.get(cloudIdx));
			imagePoints.add(rightPointList.get(match.trainIdx).pt);
		}
		opMat.fromList(objectPoints);
		ipMat.fromList(imagePoints);

		Mat rotvec = new Mat(3, 1, CvType.CV_64F);
		Mat rot = new Mat(3, 3, CvType.CV_64F);
		Mat t = new Mat(3, 1, CvType.CV_64F);
		Calib3d.solvePnPRansac(opMat, ipMat, cameraMat, new MatOfDouble(), rotvec, t);
		Calib3d.Rodrigues(rotvec, rot);
		Mat P = computeProjMat(cameraMat, rot, t);
		right.setProj(P);
		return P;
	}

	/**
	 * ����ͶӰ����
	 * 
	 * @param K ����ڲξ���
	 * @param R ��ת����
	 * @param T ƽ�ƾ���
	 * @return ���ͶӰ����
	 */
	private Mat computeProjMat(Mat K, Mat R, Mat T) {
		Mat Proj = new Mat(3, 4, CvType.CV_64F);
		Mat RT = new Mat();
		RT.push_back(R.t());
		RT.push_back(T.t());
		Core.gemm(K, RT.t(), 1, new Mat(), 0, Proj, 0);
		double test[] = new double[12];
		Proj.get(0, 0, test);
		return Proj;
	}

	/**
	 * ���������ת��Ϊ����������ֵ
	 * 
	 * @param raw ����������
	 * @return ת�������������ֵ
	 */
	private Mat divideLast(Mat raw) {
		Mat pc = new Mat();
		for (int i = 0; i < raw.cols(); i++) {
			Mat col = new Mat(4, 1, CvType.CV_32F);
			Core.divide(raw.col(i), new Scalar(raw.col(i).get(3, 0)), col);
			pc.push_back(col.t());
		}
		return pc.colRange(0, 3);
	}

	/**
	 * ��mask��Ӧλ��Ϊ1�ĵ�ɸ��
	 * 
	 * @param ptlist ��ɸѡ�ĵ��б�
	 * @param mask   mask��Mat��0����ƥ�䣬��Ҫȥ����1��ʾƥ�䣬��Ҫ����
	 */
	private void maskoutPoints(List<Point> ptlist, Mat mask) {
		LinkedList<Point> copy = new LinkedList<Point>();
		copy.addAll(ptlist);
		ptlist.clear();
		for (int i = 0; i < mask.height(); i++) {
			if (mask.get(i, 0)[0] > 0) {
				ptlist.add(copy.get(i));
			}
		}
	}

	public Mat getPointCloud() {
		return pointCloud;
	}
}
