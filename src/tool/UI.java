package tool;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.highgui.VideoCapture;

public class UI {
	
	/**
	 * 	����Ƶ��ȡ�̶����֡����ͼƬ�����������һ֡������List<Mat>
	 * @param filePath ��Ƶ�Ĵ洢·��
	 * @param frameRate �������֡ȡһ��ͼ��
	 * @param imageList ���ػ�ȡ����֡���б�����imageList 
	 */
	public static void getMatListFromVideo(String filePath,int frameRate,List<Mat> imageList,Mat lastImage){
		try {
			VideoCapture capture = new VideoCapture(filePath);// ��ȡ��Ƶ 
			if (!capture.isOpened()) {
				throw new Exception("��Ƶ�ļ���ʧ�ܡ�");
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
						if(count%frameRate!=1) {
							imageList.add(lastImage);
						}
						capture.release();
						break;
					}
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
}
