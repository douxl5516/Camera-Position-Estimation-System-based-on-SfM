package utilities;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.KeyPoint;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import type.ImageData;
import type.MatchInfo;

public class Reconstruction {
	
	/**
	 * ��ȡ�����㺯��
	 * 
	 * @param imgList ����Mat���б�������ÿһ��ͼƬ������������ȡ
	 * @param imgDataList �����ȡ���ݵ��б�����ͼƬ�������㡢�����������Ӻ���ɫ
	 */
	public static void extractFeatures(List<Mat> imgList, List<ImageData> imgDataList) {
		for(int i=0;i<imgList.size();i++) {
			ImageData temp=detectFeature(imgList.get(i));
			if(temp.getKeyPoint().height()<10) {//���һ��ͼƬ��⵽����������С��10��������
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
	 */
	public static MatchInfo matchFeatures(ImageData query,ImageData train) {
		DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(query.getDescriptors(),train.getDescriptors(),matches);
        List<DMatch> matchesList = matches.toList();
        List<KeyPoint> kpList1 = query.getKeyPoint().toList();
        List<KeyPoint> kpList2 = train.getKeyPoint().toList();
        LinkedList<Point> points1 = new LinkedList<>();
        LinkedList<Point> points2 = new LinkedList<>();
        for (int i=0;i<matchesList.size();i++){
            points1.addLast(kpList1.get(matchesList.get(i).queryIdx).pt);
            points2.addLast(kpList2.get(matchesList.get(i).trainIdx).pt);
        }
        MatOfPoint2f kp1 = new MatOfPoint2f();
        MatOfPoint2f kp2 = new MatOfPoint2f();
        kp1.fromList(points1);
        kp2.fromList(points2);
        Mat inliner = new Mat();
        Mat F = Calib3d.findHomography(kp1,kp2,Calib3d.FM_RANSAC,3,inliner); //������inliner��ͼƬ�ϵı任����
//        Mat F = Calib3d.findFundamentalMat(kp1,kp2,Calib3d.FM_RANSAC,3,0.99, inliner);	//������inliner�ǻ�������
        List<Byte> isInliner = new ArrayList<>();
        Converters.Mat_to_vector_uchar(inliner,isInliner);
        LinkedList<DMatch> good_matches = new LinkedList<>();
        MatOfDMatch gm = new MatOfDMatch();
        for (int i=0;i<isInliner.size();i++){
            if(isInliner.get(i)!=0){
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
	private static ImageData detectFeature(Mat image){
		int channels=image.channels();
        Mat img = new Mat(image.height(), image.width(), CvType.CV_8UC3);
        FeatureDetector detector = FeatureDetector.create(FeatureDetector.SIFT);
        DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.SIFT);
        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        Mat descriptors = new Mat();
        
        switch (channels) {
		case 3:
			Imgproc.cvtColor(image, img, Imgproc.COLOR_RGB2BGR, 3);	//ת��ΪBGR��ɫģʽ��Ϊ��Ч�ʿ���
			break;
		case 4:
			Imgproc.cvtColor(image, img, Imgproc.COLOR_RGBA2BGR, 3);
			break;
		}
        
        detector.detect(img, keypoints);
        extractor.compute(img, keypoints, descriptors);
        Mat color = extractKeypointColor(keypoints, image);
        return ImageData.newInstance(keypoints,descriptors,color);
    }
	
	/**
	 * ��������ɫ��ȡ
	 * 
	 * @param keyPoint �������б�
	 * @param img ԭͼ��
	 * @return ��������ɫ��Mat
	 */
    private static Mat extractKeypointColor(MatOfKeyPoint keyPoint, Mat img){
        int channels=img.channels();
        Mat color = null;
        
        switch (channels) {
		case 3:
			color = new Mat(keyPoint.height(),1,CvType.CV_32FC3);
			break;
		case 4:
			color = new Mat(keyPoint.height(),1,CvType.CV_32FC4);
			break;
		default:
			try {
				throw new Exception("�������������ͼ������ͨ������ͨ����");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
    	
        float scale = 1.0f/256.0f;
        for(int i=0;i<keyPoint.toList().size();i++){
            int y = (int)keyPoint.toList().get(i).pt.y;
            int x = (int)keyPoint.toList().get(i).pt.x;
            double[] tmp = img.get(y, x);
            for(int j=0;j<color.channels();j++){
                tmp[j] *= scale;
            }
            color.put(i,0,tmp);
        }
        return color.clone();
    }

}
