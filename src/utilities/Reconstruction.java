package utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfKeyPoint;
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

	private final float scale = 1 / 256;

	public Reconstruction(Mat cameraMat, List<Mat> imageList, List<ImageData> imageDataList,
			List<MatchInfo> matchesList) {
		this.cameraMat = cameraMat;

		InitStructure(imageDataList.get(0), imageDataList.get(1), matchesList.get(0).getMatches(), imageList.get(1));
		System.out.println("����size:" + pointCloud.size());
//		System.out.println(pointCloud.dump());
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
		color = new Mat(gm.toList().size(), 1, CvType.CV_32FC3);
		MatOfKeyPoint leftPoint = left.getKeyPoint(); // ԭ��ͼ�ؼ����б�
		MatOfKeyPoint rightPoint = right.getKeyPoint(); // ԭ��ͼ�ؼ����б�
		Mat rot1 = Mat.eye(3, 3, CvType.CV_64F); // ��ͼ�����ת����
		Mat t1 = Mat.zeros(3, 1, CvType.CV_64F); // ��ͼ���ƽ�ƾ���
		Mat rot2 = new Mat(3, 3, CvType.CV_64F); // ��ͼ�����ת����
		Mat t2 = new Mat(3, 1, CvType.CV_64F); // ��ͼ���ƽ�ƾ���

		LinkedList<Point> ptlist1 = new LinkedList<>(); // ��ƥ������ͼ�������б�
		LinkedList<Point> ptlist2 = new LinkedList<>(); // ��ƥ������ͼ�������б�
		MatOfPoint2f kp1 = new MatOfPoint2f();
		MatOfPoint2f kp2 = new MatOfPoint2f();
		int[] left_idx = new int[leftPoint.height()];
		int[] right_idx = new int[rightPoint.height()];
		Arrays.fill(left_idx, -1);
		Arrays.fill(right_idx, -1);

		// ��ȡƥ���Բ���������
		for (int i = 0; i < gm.toList().size(); i++) {
			DMatch match = gm.toList().get(i);
			ptlist1.addLast(leftPoint.toList().get(match.queryIdx).pt);
			ptlist2.addLast(rightPoint.toList().get(match.trainIdx).pt);
			left_idx[match.queryIdx] = i;
			right_idx[match.trainIdx] = i;

			// ȡ��i��ƥ�������ͼ�ĵ�������Ϊ��ɫ��ȡֵ
			Point pt = rightPoint.toList().get(match.trainIdx).pt;
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

		Mat E = Calib3d.findEssentialMat(kp1, kp2, cameraMat);
		Calib3d.recoverPose(E, kp1, kp2, cameraMat, rot2, t2);

		kp1.release();
		kp2.release();
		kp1.fromList(ptlist1);
		kp2.fromList(ptlist1);

		Mat P1 = computeProjMat(cameraMat, rot1, t1);
		Mat P2 = computeProjMat(cameraMat, rot2, t2);
		Mat pc_raw = new Mat();
		Calib3d.triangulatePoints(P1, P2, kp1, kp2, pc_raw);
		pointCloud = divideLast(pc_raw);
		LastP = P2.clone();
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
		MatOfKeyPoint leftPoint = left.getKeyPoint(); // ��ͼԭ�ؼ����б�
		MatOfKeyPoint rightPoint = right.getKeyPoint(); // ��ͼԭ�ؼ����б�
		LinkedList<Point3> pclist = new LinkedList<>(); // �µĵ����б�
		LinkedList<Point> right_inPC = new LinkedList<>(); // �ڵ����е���ͼ�Ĺؼ����б�
		LinkedList<Point> leftlist = new LinkedList<>();// ��ƥ������е���ͼ������
		LinkedList<Point> rightist = new LinkedList<>();// ��ƥ������е���ͼ������
		MatOfPoint3f pc = new MatOfPoint3f();
		MatOfPoint2f kp1 = new MatOfPoint2f();
		MatOfPoint2f kp2 = new MatOfPoint2f();
		int count = pointCloud.height(); // ���Ƶ���
		int[] left_idx = correspondence_idx.get(correspondence_idx.size() - 1); // ���һ��ͼ���ƥ������
		int[] right_idx = new int[rightPoint.height()];
		Arrays.fill(right_idx, -1);

		for (int i = 0; i < matches.toList().size(); i++) {
			DMatch match = matches.toList().get(i);
			if (left_idx[match.queryIdx] >= 0) { // ������ṩ��ƥ�����е������ԭ��ƥ��������
				pclist.addLast(pc3f.toList().get(left_idx[match.queryIdx]));// ��ԭ�е����еĸõ���ӵ��µĵ�����
				right_inPC.addLast(rightPoint.toList().get(match.trainIdx).pt);
				right_idx[match.trainIdx] = left_idx[match.queryIdx];
			} else {// ������ṩ��ƥ�����е���㲻��ԭ��ƥ��������
				leftlist.addLast(leftPoint.toList().get(match.queryIdx).pt);
				rightist.addLast(rightPoint.toList().get(match.trainIdx).pt);
				left_idx[match.queryIdx] = count;
				right_idx[match.trainIdx] = count;
				Point pt = rightPoint.toList().get(match.trainIdx).pt;
				double[] tmp = img.get((int) pt.y, (int) pt.x);
				tmp[0] *= scale;
				tmp[1] *= scale;
				tmp[2] *= scale;
				Mat dummy = new Mat(1, 1, CvType.CV_32FC3);
				color.push_back(dummy);
				count++;
			}
		}
		correspondence_idx.add(right_idx);
		pc.fromList(pclist);
		kp2.fromList(right_inPC);
		Mat rotvec = new Mat(3, 1, CvType.CV_64F);
		Mat rot = new Mat(3, 3, CvType.CV_64F);
		Mat t = new Mat(3, 1, CvType.CV_64F);
		Calib3d.solvePnP(pc, kp2, cameraMat, new MatOfDouble(), rotvec, t);
		kp1.fromList(leftlist);
		kp2.fromList(rightist);
		Calib3d.Rodrigues(rotvec, rot);
		Mat P = computeProjMat(cameraMat, rot, t);
		Mat pc_raw = new Mat();
		Calib3d.triangulatePoints(LastP, P, kp1, kp2, pc_raw);
		Mat new_PC = divideLast(pc_raw);
		pointCloud.push_back(new_PC);
		LastP = P.clone();
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
		MatOfKeyPoint leftPoint = left.getKeyPoint(); // ��ͼԭ�ؼ����б�
		MatOfKeyPoint rightPoint = right.getKeyPoint(); // ��ͼԭ�ؼ����б�
		LinkedList<Point3> pclist = new LinkedList<>(); // �µĵ����б�
		LinkedList<Point> right_inPC = new LinkedList<>(); // �ڵ����е���ͼ�Ĺؼ����б�
		LinkedList<Point> leftlist = new LinkedList<>();// ��ƥ������е���ͼ������
		LinkedList<Point> rightist = new LinkedList<>();// ��ƥ������е���ͼ������
		MatOfPoint3f pc = new MatOfPoint3f();
		MatOfPoint2f kp1 = new MatOfPoint2f();
		MatOfPoint2f kp2 = new MatOfPoint2f();
		int count = pointCloud.height(); // ���Ƶ���
		int[] left_idx = correspondence_idx.get(correspondence_idx.size() - 1); // ���һ��ͼ���ƥ������
		int[] right_idx = new int[rightPoint.height()];
		Arrays.fill(right_idx, -1);

		for (int i = 0; i < matches.toList().size(); i++) {
			DMatch match = matches.toList().get(i);
			if (left_idx[match.queryIdx] >= 0) { // ������ṩ��ƥ�����е������ԭ��ƥ��������
				pclist.addLast(pc3f.toList().get(left_idx[match.queryIdx]));// ��ԭ�е����еĸõ���ӵ��µĵ�����
				right_inPC.addLast(rightPoint.toList().get(match.trainIdx).pt);
				right_idx[match.trainIdx] = left_idx[match.queryIdx];
			} else {// ������ṩ��ƥ�����е���㲻��ԭ��ƥ��������
				leftlist.addLast(leftPoint.toList().get(match.queryIdx).pt);
				rightist.addLast(rightPoint.toList().get(match.trainIdx).pt);
				left_idx[match.queryIdx] = count;
				right_idx[match.trainIdx] = count;
				Point pt = rightPoint.toList().get(match.trainIdx).pt;
				double[] tmp = img.get((int) pt.y, (int) pt.x);
				tmp[0] *= scale;
				tmp[1] *= scale;
				tmp[2] *= scale;
				Mat dummy = new Mat(1, 1, CvType.CV_32FC3);
				color.push_back(dummy);
				count++;
			}
		}
		correspondence_idx.add(right_idx);
		pc.fromList(pclist);
		kp2.fromList(right_inPC);
		Mat rotvec = new Mat(3, 1, CvType.CV_64F);
		Mat rot = new Mat(3, 3, CvType.CV_64F);
		Mat t = new Mat(3, 1, CvType.CV_64F);
		Calib3d.solvePnP(pc, kp2, cameraMat, new MatOfDouble(), rotvec, t);
		kp1.fromList(leftlist);
		kp2.fromList(rightist);
		Calib3d.Rodrigues(rotvec, rot);
		Mat P = computeProjMat(cameraMat, rot, t);
		Mat pc_raw = new Mat();
		Calib3d.triangulatePoints(LastP, P, kp1, kp2, pc_raw);
		Mat new_PC = divideLast(pc_raw);
		pointCloud.push_back(new_PC);
		LastP = P.clone();
		return pointCloud.clone();
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

	public Mat getPointCloud() {
		return pointCloud;
	}
}
