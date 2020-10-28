package naji.cardreader;

import android.graphics.Bitmap;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;

import java.util.Map;

public class DTOs {
    /**
     * a DTO For Storing Aligned Images & Their Properties
     */
    static class AlignedImage {
        Mat image;
        Boolean niceHomography;
        double homographyDeterminant;
        int templateId;
        String templateName;
        Bitmap serialNumberBitmap;

        AlignedImage(Mat image, Boolean niceHomography, double homographyDeterminant, int templateId, String templateName) {
            this.image = image;
            this.niceHomography = niceHomography;
            this.homographyDeterminant = homographyDeterminant;
            this.templateId = templateId;
            this.templateName = templateName;
        }
    }

    /**
     * a DTO For Storing Template Images & Their Properties
     */
    static class Template {
        int templateResourceId;
        String templateResourceName;
        TemplateInfo templateInfo;
        Mat template;
        MatOfKeyPoint templateKeypoints;
        Mat templateDescriptors;

        Template(int templateResourceId, String templateResourceName, TemplateInfo templateInfo, Mat template, MatOfKeyPoint templateKeypoints, Mat templateDescriptors) {
            this.templateResourceId = templateResourceId;
            this.templateResourceName = templateResourceName;
            this.templateInfo = templateInfo;
            this.template = template;
            this.templateKeypoints = templateKeypoints;
            this.templateDescriptors = templateDescriptors;
        }
    }

    /**
     * a DTO For Storing Tesseract APIs To Use Alongside Each Other
     */
    static class TesseractAPI {
        String apiName;
        TessBaseAPI api;

        TesseractAPI(String apiName, TessBaseAPI api) {
            this.apiName = apiName;
            this.api = api;
        }
    }

    /**
     * Extracted Texts Using Tesseract Are Stored In This DTO To Use Later
     */
    static class ExtractedText {
        String tessAPIName;
        String text;
        int confidence;
        AlignedImage alignedImage;

        ExtractedText(String tessAPIName, String text, int confidence, AlignedImage alignedImage) {
            this.tessAPIName = tessAPIName;
            this.text = text;
            this.confidence = confidence;
            this.alignedImage = alignedImage;
        }
    }

    /**
     * Extracted Texts Using Tesseract Are Stored In This DTO To Use Later
     */
    static class TemplateInfo {
        String templateName;
        int XLeft;
        int XRight;
        int YTop;
        int YBottom;
        int[] characterLengths;

        TemplateInfo(String templateName, int XLeft, int XRight, int YTop, int YBottom, int[] characterLengths) {
            this.templateName = templateName;
            this.XLeft = XLeft;
            this.XRight = XRight;
            this.YTop = YTop;
            this.YBottom = YBottom;
            this.characterLengths = characterLengths;
        }
    }
}
