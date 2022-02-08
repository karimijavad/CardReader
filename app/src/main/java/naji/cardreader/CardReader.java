package naji.cardreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;


import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Feature2D;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import naji.cardreader.DTOs.AlignedImage;
import naji.cardreader.DTOs.ExtractedText;
import naji.cardreader.DTOs.Template;
import naji.cardreader.DTOs.TemplateInfo;
import naji.cardreader.DTOs.TesseractAPI;

import static org.opencv.imgproc.Imgproc.MORPH_CLOSE;
import static org.opencv.imgproc.Imgproc.MORPH_OPEN;
import static org.opencv.imgproc.Imgproc.THRESH_OTSU;

/**
 * Main Activity For Capturing Card Images & OCRing Their Serial Numbers
 *
 * @author Mohammad M. Haji-Esmaeili
 * <p>
 * This is a sample application to capture images from cards and extract serial numbers from them.
 * <p>
 * To add a new card you should first add the scanned and cleaned image in the `Resources/raw` folder,
 * The size should be nearly the same as the other cards already inside.
 * a Card is called a `template` in our system since it's main function is to serve as a template to recognize an object (Card, Object, Text, ...)
 * You should then add the serial number position for the card to the `template_serial_number_positions.xml` file.
 * The template file names (In the `Resources/raw` folder) should match the names described in the `template_serial_number_positions.xml` file.
 * <p>
 * Supporting a new font (and probably a new Card) is out of the scope of this code and should be referred to the @author of the code.
 */
public class CardReader extends AppCompatActivity {
    static final int REQUEST_CAMERA_PERMISSION_CODE = 1;
    static final int REQUEST_TAKE_PHOTO_CODE = 2;

    /**
     * Core
     */
    Resources resources;

    /**
     * UI Related
     */
    ImageView imageView = null;
    ImageView serialNumberView = null;
    TextView textViewEnglish = null;
    Button captureImageButton;
    Uri imageFileURI;


    /**
     * OpenCV Related
     */
    Feature2D FEATURE_EXTRACTOR;
    DescriptorMatcher MATCHER;
    ArrayList<Template> TEMPLATES = new ArrayList<>();
    Map<String, TemplateInfo> TEMPLATE_INFORMATIONS = new HashMap<>();


    /**
     * Tesseract Related
     */
    ArrayList<TesseractAPI> TESS_APIS = new ArrayList<>();

    /**
     * Called After OpenCV Initialization is Successful
     */
    private final BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                // Now We Can Call OpenCV Code
                loadTemplatesAndExtractFeatures();
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    /**
     * Pretty Self Explanatory!
     */
    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    /**
     * Called After OpenCV Has Initialized and is Ready To Function
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.resources = getResources();

        initializeUI();

        // Load The Serial Number Positions From XML
        readSerialNumberPositionsFromXML();

        //Check Access Permissions
        checkPermissions();

        //Copy Tesseract Models From Raw Resources To System Storage
        try {
            initializeTesseractAPI("eng");
            initializeTesseractAPI("fas");
        } catch (Exception e) {
            e.printStackTrace();
            if (TESS_APIS.size() <= 1) {
                System.out.println("One of the Models Could Not be Loaded... Exiting...");
                finish();
            }
        }

        //If OpenCV Has Been Configured Properly, Then Continue Executing The Code
        if (OpenCVLoader.initDebug()) {
//            Toast.makeText(this, "OpenCV Is Configured or Connected Successfully.", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "کتابخانه‌های اپلیکیشن به‌درستی بارگذاری شدند.", Toast.LENGTH_SHORT).show();
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
//            Toast.makeText(this, "OpenCV Not Working Or Loaded.", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "خطا در بارگذاری کتابخانه‌های اپلیکیشن.", Toast.LENGTH_SHORT).show();
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, baseLoaderCallback);
        }
    }

    /**
     * Initializes The UI!
     */
    public void initializeUI() {
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(R.layout.activity_image_capturer);
        captureImageButton = this.findViewById(R.id.button);
        imageView = findViewById(R.id.imageView);
        serialNumberView = findViewById(R.id.serialNumberView);
        textViewEnglish = findViewById(R.id.textViewEnglish);
        textViewEnglish.setText("");

        //On Clicking The `Capture` Button, Take The Picture & Send It For Text Extraction
        captureImageButton.setOnClickListener(v -> {
            try {
                dispatchTakePictureIntent();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Checks Whether We Have Access To The Camera
     */
    public void checkPermissions() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * Alert The User About Camera Access Permission
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission has been denied by user.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Permission has been granted by user.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * We Store Each Templates Hard Coded Info Inside a Map To Use Later
     */
    public void readSerialNumberPositionsFromXML() {
        try {
            XmlResourceParser parser = resources.getXml(R.xml.template_serial_number_positions);

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    if (parser.getName().equals("template")) {
                        String templateName = parser.getAttributeValue(null, "name");
                        parser.next();
                        int templateXLeft = Integer.parseInt(parser.nextText());
                        parser.next();
                        int templateXRight = Integer.parseInt(parser.nextText());
                        parser.next();
                        int templateYTop = Integer.parseInt(parser.nextText());
                        parser.next();
                        int templateYBottom = Integer.parseInt(parser.nextText());
                        parser.next();
                        int[] sortedCharacterLenghts = Arrays.stream(parser.nextText().split(",")).map(Integer::parseInt).mapToInt(Integer::intValue).boxed().sorted(Comparator.reverseOrder()).mapToInt(i -> i).toArray();

                        TemplateInfo templateInfo = new TemplateInfo(templateName, templateXLeft, templateXRight, templateYTop, templateYBottom, sortedCharacterLenghts);
                        TEMPLATE_INFORMATIONS.put(templateName, templateInfo);
                    }
                }
                parser.next();
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Request failed: " + t.toString(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Copy Tesseract Models From Raw Resources To System
     *
     * @param modelName Name Of The Tesseract Models Without Extensions
     */
    public void initializeTesseractAPI(String modelName) throws Exception {
        //This is the Main Standard Folder For Storing Tesseract Data
        File tesseractFolder = getExternalFilesDir("tesseract/tessdata/");
        if (!tesseractFolder.exists())
            tesseractFolder.mkdirs();

        //We Should Be Able To Find The Model In This Path If It Has Been Copied Here Properly (By Another Method)
        String tessDataFolderPath = tesseractFolder.toString();
        String outputTesseractModelPath = String.format("%s/%s.traineddata", tessDataFolderPath, modelName);
        File outputTesseractModelFile = new File(outputTesseractModelPath);

        //We Copy The Raw Resource Into The System Storage To Be Used Later By Tesseract
        InputStream inputTesseractModelStream = getResources().openRawResource(getResources().getIdentifier(modelName, "raw", getPackageName()));
        FileOutputStream outputTesseractModelStream = new FileOutputStream(outputTesseractModelFile);
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputTesseractModelStream.read(buffer)) != -1) {
            outputTesseractModelStream.write(buffer, 0, read);
        }
        inputTesseractModelStream.close();
        outputTesseractModelStream.flush();
        outputTesseractModelStream.close();

        //We Load The Tesseract API By Path
        TessBaseAPI tessBaseAPI;
        try {
            tessBaseAPI = new TessBaseAPI();
        } catch (Exception e) {
            throw new Exception("TessFactory Not Returning Tess Object.");
        }

        String tesseractFolderPath = getExternalFilesDir("tesseract/tessdata/").getParent();
        boolean tesseractSuccess = tessBaseAPI.init(tesseractFolderPath, modelName);

        if (!tesseractSuccess)
            throw new Exception(String.format("'%s' Tesseract Model Could Not Be Loaded...", modelName));

        //We Add Some Settings For Better OCR
        System.out.println(String.format("'%s' Training File Loaded", modelName));
        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);
        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE);
        if (modelName.contains("eng"))
            tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        else
            tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789٠١٢٣٤٥٦٧٨٩");

//        tessBaseAPI.setDebug(true);
        TESS_APIS.add(new TesseractAPI(modelName, tessBaseAPI));
    }

    /**
     * Initializes The Resources Necessary For OpenCV To Work
     */
    public void loadTemplatesAndExtractFeatures() {
        try {
//            FEATURE_EXTRACTOR = AKAZE.create(); // More Accurate But Slower
            FEATURE_EXTRACTOR = ORB.create(); // Accurate Enough And Faster
            MATCHER = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

            Field[] fields = R.raw.class.getFields();
            for (Field field : fields) {
                if (field.getName().contains("template_card")) {
                    int templateCardID = field.getInt(field);

                    Template template = loadTemplateAndExtractFeature(templateCardID);
                    TEMPLATES.add(template);
                }
            }
        } catch (IOException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads a Card (Called Templates) By Its Resource ID
     *
     * @param templateID The Card (File) ID
     * @return a TemplateContainer For Each Template (Card)
     */
    public Template loadTemplateAndExtractFeature(int templateID) throws IOException {
        String templateName = getResources().getResourceEntryName(templateID);
        TemplateInfo templateInfo = TEMPLATE_INFORMATIONS.get(templateName);

        Mat template = Utils.loadResource(this, templateID, Imgcodecs.IMREAD_COLOR);
        Mat templateGray = new Mat(template.height(), template.width(), CvType.CV_8UC1);
        Imgproc.cvtColor(template, templateGray, Imgproc.COLOR_BGR2GRAY);
        MatOfKeyPoint templateKeypoints = new MatOfKeyPoint();
        Mat templateDescriptors = new Mat();
        FEATURE_EXTRACTOR.detectAndCompute(templateGray, new Mat(), templateKeypoints, templateDescriptors, false);

        return new Template(templateID, templateName, templateInfo, template, templateKeypoints, templateDescriptors);
    }

    /**
     * If You Want To Cache The Models For Later Use, Then Modify This Code To Not Delete Temp Files
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyOpenCVResources();
        if (!isChangingConfigurations()) {
            deleteTempFiles(getExternalFilesDir(Environment.DIRECTORY_PICTURES));
        }
    }

    /**
     * Deletes The Temporary Images Stored On Storage
     */
    public void deleteTempFiles(File file) {
        if (file != null) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isDirectory()) {
                            deleteTempFiles(f);
                        } else {
                            f.delete();
                        }
                    }
                }
            }
            file.delete();
        }
    }

    /**
     * Destroys OpenCV Resources To Prevent Memory Overflow
     */
    public void destroyOpenCVResources() {
        for (Template template : TEMPLATES) {
            template.template.release();
            template.templateDescriptors.release();
            template.templateKeypoints.release();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     * Initializes an Intent For Taking Pictures
     */
    public void dispatchTakePictureIntent() throws IOException {
        Intent captureImageIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure That There's a Camera Activity to Handle The Intent
        if (captureImageIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File Where The Photo Should Go
            File imageFile = createImageFile();
            imageFileURI = FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()), BuildConfig.APPLICATION_ID + ".fileprovider", imageFile);
            captureImageIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageFileURI);
            startActivityForResult(captureImageIntent, REQUEST_TAKE_PHOTO_CODE);
        }
    }

    /**
     * Create a Temporary Image File To Be Processed
     */
    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "NajiCardReader_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        if (storageDir != null) {
            Toast.makeText(this, String.format("Images Are Stored in: '%s'", storageDir.toString()), Toast.LENGTH_SHORT).show();
        }
        return image;
    }

    /**
     * Called When a Captured Image Is Ready To Be Processed
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_TAKE_PHOTO_CODE) {
            try (InputStream inputStream = getContentResolver().openInputStream(imageFileURI)) {
                Bitmap photo = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageFileURI));

                ExifInterface exifInterface = new ExifInterface(inputStream);
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

                Bitmap rotatedBitmap;
                switch (orientation) {

                    case ExifInterface.ORIENTATION_ROTATE_90:
                        rotatedBitmap = rotateImage(photo, 90);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        rotatedBitmap = rotateImage(photo, 180);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_270:
                        rotatedBitmap = rotateImage(photo, 270);
                        break;

                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                        rotatedBitmap = photo;
                }

                processBitmapImage(rotatedBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Given a Bitmap Image, It OCRs The Image & Finds The Serial Number Inside (If It Exists)
     */
    public void processBitmapImage(Bitmap cameraImageBitmap) {
        Mat cameraImage = new Mat();

        try {
            Bitmap cameraImageBitmapCopy = cameraImageBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Utils.bitmapToMat(cameraImageBitmapCopy, cameraImage);

            // Get The Best Aligned Image By Templates Possible
            List<AlignedImage> alignedImages = alignImageWithTemplates(cameraImage);

            ExtractedText extractedText = null;
            if (alignedImages.size() > 0)
                extractedText = extractSerialNumberFromAlignedImages(alignedImages);

            //Show The Aligned Image On The Screen
            if (extractedText != null) {
                Bitmap alignedImageBitmap = Bitmap.createBitmap(extractedText.alignedImage.image.width(), extractedText.alignedImage.image.height(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(extractedText.alignedImage.image, alignedImageBitmap);
                this.imageView.setImageBitmap(alignedImageBitmap);
                this.serialNumberView.setImageBitmap(extractedText.alignedImage.serialNumberBitmap);
                this.textViewEnglish.setText("شماره کارت: " + extractedText.text);
            } else {
                this.imageView.setImageBitmap(cameraImageBitmap);
                this.serialNumberView.setImageBitmap(null);
                this.textViewEnglish.setText("");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cameraImage.release();
        }
    }

    /**
     * Given a List of Aligned Images, Extracts Serial Number From Them And Tries To Find The Most Correct Text From Them, If Not, Returns Null
     */
    public ExtractedText extractSerialNumberFromAlignedImages(List<AlignedImage> alignedImages) {
        List<ExtractedText> extractedTexts = new ArrayList<>();
        for (AlignedImage alignedImage : alignedImages) {
            List<ExtractedText> extractedText = extractSerialNumberFromAlignedImage(alignedImage);
            extractedTexts.addAll(extractedText);
        }

        //Choose The Best Extracted Text Based On Text Length & Confidence
        ExtractedText bestExtractedText = null;
        if (extractedTexts.size() > 0) {
            Collections.sort(extractedTexts, (t1, t2) -> t2.confidence - t1.confidence);

            bestExtractedText = extractedTexts.stream()
                    .filter(x -> x.text.length() >= 10)
                    .max(Comparator.comparingInt(x -> x.confidence))
                    .orElse(null);
        }

        return bestExtractedText;
    }

    /**
     * Aligns an Image With All of The Templates We Have
     */
    public List<AlignedImage> alignImageWithTemplates(Mat image) {
        List<AlignedImage> alignedImages = new ArrayList<>();

        for (Template template : TEMPLATES) {
            AlignedImage alignedImage = alignImageWithTemplate(image, template);

            //Crop The Serial Number Area From The Aligned Image & Add It To The Object
            if (alignedImage != null)
                alignedImage.serialNumberBitmap = cropSerialNumberArea(alignedImage);
            alignedImages.add(alignedImage);
        }

        return alignedImages;
    }


    /**
     * Given an Image, It Aligns The Image With a Template (Needed For Extracting The Serial Number Region)
     */
    @SuppressLint("DefaultLocale")
    public AlignedImage alignImageWithTemplate(Mat image, Template template) {
        Mat imageGray = new Mat();
        Imgproc.cvtColor(image, imageGray, Imgproc.COLOR_BGR2GRAY);

        int image_width = image.width();
        float scale = ((float) template.template.width()) / image_width;
        Imgproc.resize(image, image, new Size(), scale, scale);
        Imgproc.resize(imageGray, imageGray, new Size(), scale, scale);

        MatOfKeyPoint imageKeypoints = new MatOfKeyPoint();
        Mat imageDescriptors = new Mat();
        MatOfDMatch matches = new MatOfDMatch();

        try {
            //Detecting Image Features
            FEATURE_EXTRACTOR.detectAndCompute(imageGray, new Mat(), imageKeypoints, imageDescriptors, false);

            //Matching Features From Template To Image
            MATCHER.match(template.templateDescriptors, imageDescriptors, matches);
            List<DMatch> matchesList = matches.toList();

            double max_dist = 0.0;
            double min_dist = 100.0;

            for (int i = 0; i < matchesList.size(); i++) {
                double dist = (double) matchesList.get(i).distance;
                if (dist < min_dist)
                    min_dist = dist;
                if (dist > max_dist)
                    max_dist = dist;
            }

            LinkedList<DMatch> goodMatchesList = new LinkedList<DMatch>();
            for (int i = 0; i < matchesList.size(); i++) {
                if (matchesList.get(i).distance < (3 * min_dist)) {
                    goodMatchesList.addLast(matchesList.get(i));
                }
            }

            if (goodMatchesList.size() > 0) {
                MatOfDMatch goodMatchesMat = new MatOfDMatch();
                goodMatchesMat.fromList(goodMatchesList);

                LinkedList<Point> templateList = new LinkedList<>();
                LinkedList<Point> imageList = new LinkedList<>();

                List<KeyPoint> templateKeypointsList = template.templateKeypoints.toList();
                List<KeyPoint> imageKeypointsList = imageKeypoints.toList();

                for (int i = 0; i < goodMatchesList.size(); i++) {
                    templateList.addLast(templateKeypointsList.get(goodMatchesList.get(i).queryIdx).pt);
                    imageList.addLast(imageKeypointsList.get(goodMatchesList.get(i).trainIdx).pt);
                }

                MatOfPoint2f templatePoints = new MatOfPoint2f();
                templatePoints.fromList(templateList);

                MatOfPoint2f imagePoints = new MatOfPoint2f();
                imagePoints.fromList(imageList);

                Mat homography = Calib3d.findHomography(imagePoints, templatePoints, Calib3d.RANSAC);
                double[] homographyProperties = getHomographyProperties(homography);
                boolean niceHomography = determineNiceHomography(homographyProperties);
                double determinant = homographyProperties[0];
                System.out.println(String.format("%s Homography, Determinant = %.2f", niceHomography ? "Nice" : "Bad", determinant));

                Mat outputMat = new Mat();
                Imgproc.warpPerspective(image, outputMat, homography, new Size(template.template.width(), template.template.height()));
                Imgproc.resize(outputMat, outputMat, new Size(template.template.width(), template.template.height()));
                System.out.println(String.format("Width %s, Height %s ", outputMat.width(), outputMat.height()));

                return new AlignedImage(outputMat, niceHomography, determinant, template.templateResourceId, template.templateResourceName);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            imageKeypoints.release();
            imageDescriptors.release();
            matches.release();
            imageGray.release();
        }
    }

    /**
     * Extracts The Properties Of The Homography Matrix Including The Determinant
     */
    public double[] getHomographyProperties(Mat homography) {
        double determinant = homography.get(0, 0)[0] * homography.get(1, 1)[0] - homography.get(1, 0)[0] * homography.get(0, 1)[0];
        System.out.println(String.format("The Determinant Is => %.2f", determinant));

        double N1 = Math.sqrt(homography.get(0, 0)[0] * homography.get(0, 0)[0] + homography.get(1, 0)[0] * homography.get(1, 0)[0]);
        double N2 = Math.sqrt(homography.get(0, 1)[0] * homography.get(0, 1)[0] + homography.get(1, 1)[0] * homography.get(1, 1)[0]);
        double N3 = Math.sqrt(homography.get(2, 0)[0] * homography.get(2, 0)[0] + homography.get(2, 1)[0] * homography.get(2, 1)[0]);

        return new double[]{determinant, N1, N2, N3};
    }

    /**
     * Property of an affine (and projective?) transformation:
     * If the determinant of the top-left 2x2 matrix is > 0 the transformation is orientation-preserving.
     * Else if the determinant is < 0, it is orientation-reversing.
     */
    public boolean determineNiceHomography(double[] homographyProperties) {
        double determinant = homographyProperties[0];
        double N1 = homographyProperties[1];
        double N2 = homographyProperties[2];
        double N3 = homographyProperties[3];

        if (determinant < 0)
            return false;

        if (N1 > 4 || N1 < 0.1)
            return false;

        if (N2 > 4 || N2 < 0.1)
            return false;

        if (N3 > 0.002)
            return false;

        return true;
    }

    /**
     * Crops The Given Image To Get The Serial Number Area. Each Template (Card) Has Its Own Hard Coded Serial Number Positions
     */
    public Bitmap cropSerialNumberArea(AlignedImage alignedImage) {
        int XLeft, XRight, YTop, YBottom, distanceX, distanceY;
        Rect rectCrop;

        TemplateInfo templateInfo = TEMPLATE_INFORMATIONS.get(alignedImage.templateName);

        XLeft = templateInfo.XLeft;
        XRight = templateInfo.XRight;
        YTop = templateInfo.YTop;
        YBottom = templateInfo.YBottom;
        distanceX = XRight - XLeft;
        distanceY = YBottom - YTop;
        rectCrop = new Rect(XLeft, YTop, distanceX, distanceY);

        Mat submat = alignedImage.image.submat(rectCrop);

        Photo.detailEnhance(submat, submat, 10, (float) 0.15);

        Imgproc.cvtColor(submat, submat, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(submat, submat, 128, 255, THRESH_OTSU);
        Imgproc.resize(submat, submat, new Size(), 2, 2);

        Imgproc.medianBlur(submat, submat, 3);
        Imgproc.morphologyEx(submat, submat, MORPH_CLOSE, Mat.ones(3, 3, CvType.CV_32F));
        Imgproc.morphologyEx(submat, submat, MORPH_OPEN, Mat.ones(3, 3, CvType.CV_32F));

        Bitmap serialNumberBitmap;
        serialNumberBitmap = Bitmap.createBitmap(submat.width(), submat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(submat, serialNumberBitmap);

        return serialNumberBitmap;
    }

    /**
     * Given a Bitmap Image, It OCRs The Image And Extract Distinguishable Numbers (or Text) From It
     */
    public ArrayList<ExtractedText> extractSerialNumberFromAlignedImage(AlignedImage alignedImage) {
        //If a SerialNumberImage Was Found Then Prepare To Show It On Page Or Else Show The Whole Image
        String extractedText;
        ArrayList<ExtractedText> extractedTexts = new ArrayList<>();

        for (TesseractAPI tessAPI : TESS_APIS) {
            try {
                tessAPI.api.setImage(alignedImage.serialNumberBitmap);
                extractedText = tessAPI.api.getUTF8Text();
                int confidence = tessAPI.api.meanConfidence();

                TemplateInfo templateInfo = TEMPLATE_INFORMATIONS.get(alignedImage.templateName);

                // Cleaning Up Extracted Texts Based On the XML Information
                extractedText = extractedText.replace(" ", "");
                for (int characterLength : templateInfo.characterLengths) {
                    int extractedTextLength = extractedText.length();
                    if (!Arrays.stream(templateInfo.characterLengths).anyMatch(i -> i == extractedTextLength)) {
                        extractedText = extractedText.substring(0, Math.min(extractedText.length(), characterLength));
                    }
                }

                extractedTexts.add(new ExtractedText(tessAPI.apiName, extractedText, confidence, alignedImage));
            } catch (Exception e) {
                System.out.println("Error in Recognizing Text.");
            }
        }
        return extractedTexts;
    }

}