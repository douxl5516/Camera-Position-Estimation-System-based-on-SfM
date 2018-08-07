package tool;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

public class UI {

	/**
	 * ����Ƶ��ȡ�̶����֡����ͼƬ�����������һ֡������List<Mat>
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
				capture.read(current_image);
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
	 * ��ͼ���б��ȡ����ͼƬ�����������һ�ţ�����List<Mat>
	 * 
	 * @param filePath  ͼ���б�Ĵ洢·��
	 * @param imageList ���ػ�ȡ����֡���б�����imageList
	 * @param lastImage ���һ��ͼƬ
	 */
	public static void getMatListFromImgList(String filePath, List<Mat> imageList, Mat lastImage) {
		try {
			BufferedReader fin = new BufferedReader(new FileReader(filePath));
			while (true) {
				String img_path = fin.readLine();
				if (img_path != null) {
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
}
