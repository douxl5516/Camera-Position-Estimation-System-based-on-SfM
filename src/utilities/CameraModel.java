package utilities;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import tool.ImageUI;

import org.opencv.calib3d.Calib3d;

public class CameraModel {

	BufferedReader fin;
	BufferedWriter fout;
	int image_count = 0; // ͼ������
	Size image_size; // ͼ��ߴ�
	Size board_size; // �궨����ÿ��ÿ�еĽǵ���
	MatOfPoint2f image_points_buf; // ����ÿ��ͼ���ϼ�⵽�Ľǵ�
	List<Mat> image_points_seq; // �����⵽�����нǵ�
	int count; // �����⵽�Ľǵ����
	String result_path; // �������洢��·��

	/**
	 * ���캯������ʼ������궨����
	 * 
	 * @param datasetPath ����궨���ݼ��б��ļ�·��
	 */
	public CameraModel(String dataset_path) {

		image_size = new Size();
		board_size = new Size(9, 6);
		image_points_buf = new MatOfPoint2f();
		image_points_seq = new ArrayList<Mat>();
		result_path = dataset_path + "/../calibrate_result.txt";

		try {
			fin = new BufferedReader(new FileReader(dataset_path));
			fout = new BufferedWriter(new FileWriter(result_path));
			while (true) {
				String img_path = fin.readLine();
				if (img_path != null) {
					image_count++;
					Mat imageInput = Highgui.imread(img_path);

					// �����һ��ͼƬʱ��ȡͼ������Ϣ
					if (image_count == 1) {
						image_size.width = imageInput.cols();
						image_size.height = imageInput.rows();
						System.out.println("ͼ���ȣ�" + image_size.width);
						System.out.println("ͼ��߶ȣ�" + image_size.height);
					}

					// ��ȡ�ǵ�
					if (Calib3d.findChessboardCorners(imageInput, board_size, image_points_buf) == false) {
						throw new Exception("can not find chessboard corners!");
					} else {
						Mat view_gray = new Mat();
						Imgproc.cvtColor(imageInput, view_gray, Imgproc.COLOR_BGR2GRAY);

						// �����ؾ�ȷ��
						// Size(5,5)���������ڵĴ�С,Size(-1,-1)��ʾû������
						// ���ĸ�����������ǵ�ĵ������̵���ֹ����������Ϊ���������ͽǵ㾫�����ߵ����
						Imgproc.cornerSubPix(view_gray, image_points_buf, new Size(5, 5), new Size(-1, -1),
								new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.1));
						image_points_seq.add(image_points_buf);

						// ���ڻ��Ʊ��ɹ��궨�Ľǵ㣬����8λ�ҶȻ��߲�ɫͼ��
						// ���ĸ������Ǳ�־λ������ָʾ����������ڽǵ��Ƿ�������̽�⵽
						// false��ʾ��δ��̽�⵽���ڽǵ㣬��ʱ��������ԲȦ��ǳ���⵽���ڽǵ�
						Calib3d.drawChessboardCorners(view_gray, board_size, image_points_buf, false);

						// չʾ���̸�ǵ��������
						// new ImageUI(view_gray, "Camera Calibration").imshow().waitKey(500);
					}
				} else {
					break;
				}
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		int total = image_points_seq.size();
		System.out.println("ͼ��������" + total);
		int CornerNum = (int) (board_size.width * board_size.height);	//ÿ��ͼƬ���ܵĽǵ���
		System.out.println("�ǵ���ȡ���");
		
		// ��ʼ�궨
		Size square_size = new Size(10, 10);	// ʵ�ʲ����õ��ı궨����ÿ�����̸�Ĵ�С
		List<Mat> object_points=new ArrayList<Mat>();	// ����궨���Ͻǵ����ά����
		// �������
		Mat cameraMatrix = new Mat(3, 3, CvType.CV_32FC1, Scalar.all(0));	// ������ڲ�������
		List<Integer> point_counts=new ArrayList<Integer>();  // ÿ��ͼ���нǵ������  
		Mat distCoeffs = new Mat(1, 5, CvType.CV_32FC1, Scalar.all(0));	// �������5������ϵ����k1,k2,p1,p2,k3
		List<Mat> tvecsMat=new ArrayList<Mat>();	// ÿ��ͼ�����ת����
		List<Mat> rvecsMat=new ArrayList<Mat>();	// ÿ��ͼ���ƽ������
		// ��ʼ���궨���Ͻǵ����ά����
		for (int t = 0; t<image_count; t++)
		{
			List<Point3> tempPointSet=new ArrayList<Point3>();
			for (int i = 0; i<board_size.height; i++)
			{
				for (int j = 0; j<board_size.width; j++)
				{
					Point3 realPoint = new Point3();
					/* ����궨�������������ϵ��z=0��ƽ���� */
					realPoint.x = i * square_size.width;
					realPoint.y = j * square_size.height;
					realPoint.z = 0;
					tempPointSet.add(realPoint);
				}
			}
			MatOfPoint3f tmpMat=new MatOfPoint3f();
			tmpMat.fromList(tempPointSet);
			object_points.add(tmpMat);
		}
		
		// ��ʼ��ÿ��ͼ���еĽǵ��������ٶ�ÿ��ͼ���ж����Կ��������ı궨��
		for (int i = 0; i<image_count; i++){
			point_counts.add(new Integer((int) (board_size.width*board_size.height)));
		}
		
		// ��ʼ�궨
		Calib3d.calibrateCamera(object_points, image_points_seq, image_size, cameraMatrix, distCoeffs, rvecsMat, tvecsMat, 0);
		System.out.println("�궨���");
		
		//���涨����      
		Mat rotation_matrix = new Mat(3, 3, CvType.CV_32FC1, Scalar.all(0));	//����ÿ��ͼ�����ת����
		try {
			fout.write("����ڲ�������"+System.getProperty("line.separator"));
			fout.write(cameraMatrix.dump()+System.getProperty("line.separator"));
			fout.write("����ϵ����"+System.getProperty("line.separator"));
			fout.write(distCoeffs.dump()+System.getProperty("line.separator"));
			for (int i = 0; i<image_count; i++)
			{
				fout.write("��"+(i + 1)+"��ͼ�����ת������"+System.getProperty("line.separator"));
				fout.write(tvecsMat.get(i).dump()+System.getProperty("line.separator"));
				// ����ת����ת��Ϊ���Ӧ����ת����
				Calib3d.Rodrigues(tvecsMat.get(i), rotation_matrix);
				fout.write("��"+(i + 1) +"��ͼ�����ת����"+System.getProperty("line.separator"));
				fout.write(rotation_matrix.dump()+System.getProperty("line.separator"));
				fout.write("��"+(i + 1)+"��ͼ���ƽ��������"+System.getProperty("line.separator"));
				fout.write(rvecsMat.get(i).dump()+System.getProperty("line.separator"));
			}
			System.out.println("�궨����Ѵ洢");
			fin.close();
			fout.flush();
			fout.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
}
