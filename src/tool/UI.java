package tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;

public class UI {

	/**
	 * ����Ƶ��ȡ�̶����֡����ͼƬ������List<Mat>
	 * 
	 * @param filePath  ��Ƶ�Ĵ洢·��
	 * @param frameRate �������֡ȡһ��ͼ��
	 * @param imageList ���ػ�ȡ����֡���б�����imageList
	 * @param lastImage ���һ��ͼƬ
	 */
	public static void getMatListFromVideo(String filePath, int frameRate, List<Mat> imageList) {
		Mat lastImage=new Mat();
		try {
			VideoCapture capture = new VideoCapture(filePath);// ��ȡ��Ƶ
			if (!capture.isOpened()) {
				throw new Exception("��Ƶ�ļ���ʧ��,����ffmpeg.dll");
			} else {
				Mat current_image = new Mat();
				int count = 0;
				while (true) {
					capture.read(current_image);
					if (!current_image.empty()) {
						if (count % frameRate == 0) {
							imageList.add(current_image.clone());
						}
						lastImage = current_image.clone();
						count++;
					} else {
						if (count % frameRate != 1) {
							imageList.add(lastImage);
						}
						capture.release();
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * ����Ƶ��ȡ����ɸѡ����������ߵ�ͼƬ������List<Mat>
	 * 
	 * @param filePath ��Ƶ�ļ����·��
	 * @param frameRate ÿ������֡��ʼѡȡ
	 * @param sampleSize ѡȡ��Ƭ����������
	 * @param imageList ���ص�ͼ���б�
	 */
	public static void getOptimalizedMatListFromVideo(String filePath, int frameRate,int sampleSize, List<Mat> imageList) {
		if(sampleSize>frameRate) {
			sampleSize=frameRate;
		}
		Mat lastImage=new Mat();
		try {
			VideoCapture capture = new VideoCapture(filePath);// ��ȡ��Ƶ
			if (!capture.isOpened()) {
				throw new Exception("��Ƶ�ļ���ʧ��,����ffmpeg.dll");
			} else {
				int count=0;
				Mat frame = new Mat();
				List<ArrayList<Mat>> bufImgList=new ArrayList<ArrayList<Mat>>();
				while (true) {
					capture.read(frame);
					L1:if (!frame.empty()) {
						lastImage=frame.clone();
						if (count % frameRate == 0) {
							ArrayList<Mat> list=new ArrayList<Mat>();
							for(int i=0;i<sampleSize;i++) {
								list.add(frame);
								capture.read(frame);
								if(frame.empty()) {
									bufImgList.add(list);
									break L1;
								}
								lastImage=frame.clone();
							}
							bufImgList.add(list);
						}
					} else {
						for(int i=0;i<bufImgList.size();i++) {
							ArrayList<Mat> temp=bufImgList.get(i);
							double maxSobel=0;
							int index=0;
							for(int j=0;j<temp.size();j++) {
								double s=Optimalize.Sobel(temp.get(i));
								if(s>maxSobel) {
									index=j;
									maxSobel=s;
								}
							}
							imageList.add(temp.get(index));
						}
						capture.release();
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * ��ͼ���б��ȡ����ͼƬ�����������һ�ţ�����List<Mat>
	 * 
	 * @param filePath  ͼ���б�Ĵ洢·��
	 * @param imageList ���ػ�ȡ����֡���б�����imageList
	 */
	public static void getMatListFromImgList(String filePath, List<Mat> imageList) {
		try {
			BufferedReader fin = new BufferedReader(new FileReader(filePath));
			while (true) {
				String img_path = fin.readLine();
				if (img_path != null&&img_path!="") {
					Mat imageInput = Imgcodecs.imread(img_path);
					imageList.add(imageInput.clone());
				} else {
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * ����ά�ؽ��ĵ��ƽ��д�����ļ�
	 * 
	 * @param filePath  �����ļ��Ĵ洢·��
	 * @param pointCloud �����б�
	 */
	public static void writePointCloud(String filePath,Mat pointCloud) {
		try {
			BufferedWriter fout = new BufferedWriter(new FileWriter(filePath));
			fout.write(pointCloud.dump());
			fout.flush();
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
