package tool;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.highgui.Highgui;

public class Format {
	
	/**
     * BufferedImageת����Mat
     * 
     * @param original Ҫת����BufferedImage
     * @param imgType bufferedImage������ �� BufferedImage.TYPE_3BYTE_BGR
     * @param matType ת����mat��type �� CvType.CV_8UC3
     * @return ת�����Mat
     */
	public static Mat BufferedImage2Mat (BufferedImage original, int imgType, int matType) {
        if (original == null) {
            throw new IllegalArgumentException("original == null");
        }
        // Don't convert if it already has correct type
        if (original.getType() != imgType) {
            // Create a buffered image
            BufferedImage image = new BufferedImage(original.getWidth(), original.getHeight(), imgType);
            // Draw the image onto the new buffer
            Graphics2D g = image.createGraphics();
            try {
                g.setComposite(AlphaComposite.Src);
                g.drawImage(original, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        byte[] pixels = ((DataBufferByte) original.getRaster().getDataBuffer()).getData();
        Mat mat = Mat.eye(original.getHeight(), original.getWidth(), matType);
        mat.put(0, 0, pixels);
        return mat;
    }
	
	/**
     *	����BufferedImageת����Mat��ʹ��ԭBufferedImage�ĸ�ʽ
     * 
     * @param original Ҫת����BufferedImage
     * @param matType ת����mat��type �� CvType.CV_8UC3
     * @return ת�����Mat
     */
	public static Mat BufferedImage2Mat (BufferedImage original, int matType) {
		return BufferedImage2Mat(original,original.getType(),matType);
	}
	
	/**
     * Matת����BufferedImage
     * 
     * @param matrix Ҫת����Mat
     * @param fileExtension ��ʽΪ ".jpg", ".png", etc
     * @return ת�����BufferedImage
     */
    public static BufferedImage Mat2BufferedImage (Mat matrix, String fileExtension) {
        // convert the matrix into a matrix of bytes appropriate for this file extension
        MatOfByte mob = new MatOfByte();
        Highgui.imencode(fileExtension, matrix, mob);
        // convert the "matrix of bytes" into a byte array
        byte[] byteArray = mob.toArray();
        BufferedImage bufImage = null;
        try {
            InputStream in = new ByteArrayInputStream(byteArray);
            bufImage = ImageIO.read(in);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bufImage;
    }
}
