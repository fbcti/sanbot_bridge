/**
 * @file        Image.java
 * @brief       Implements Image class.
 */
package com.fbcti.sanbot.bridge.robot.media;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class for storing and manipulating image data.
 *
 * The class provides functions for converting, mirroring and resizing the image. The image data is
 * stored as either or both bitmap data in the @c bitmap member variable, as binary data in the
 * @c bytes member variable. Some operations like resizing a PNG or JPEG image can not be done
 * directly on the binary data, but use an intermediate bitmap. This intermediate bitmap is stored
 * so it remains available for subsequent operations.
 *
 * The class supports the following image formats:
 * - BIMTAP
 * - RGB888
 * - RGB565
 * - GRAY8
 * - PNG
 * - JPEG
 * - WEBP
 * - YUV_NV21
 * - YUV_YUY2
 * - RAW
 *
 * The @e RAW format is not really an image format, it can be used to store arbitrary binary data
 * like depth data or infrared data. Raw data can not be converted to other image formats, and none
 * of the image formats can be converted to raw data.
 *
 * Not all operations are supported for all image types. If an operation is not supported it will
 * typically le 
 * 
 * The class also implements public methods for setting and getting an image timestamp and image 
 * meta data.
 *
 *  * @version     1.0.001
 *  * @date        9 Sep 2026
 *  * @author      Ferry Blaazer
 *  * @copyright   2026 FBCTIave the image unchanged.
 */
public final class Image
{
    /** Specifies if RGB565 bytes are packed little-endian or big-endian. */
    public static final boolean RGB565_LITLLE_ENDIAN = true;

    /** Default binary image data type. */
    public static final Type DEFAULT_BINARY_DATA_TYPE = Type.JPEG;

    /** Minimum image JPEG compression quality. */
    public static final int MIN_JPEG_QUALITY = 20;

    /** Maximum image JPEG compression quality. */
    public static final int MAX_JPEG_QUALITY = 100;

    /** Default compression quality used for JPEG images. */
    public static final int DEFAULT_JPEG_QUALITY = 80;

    /** Bitmap image data. */
    private Bitmap bitmap = null;

    /** Binary image data. */
    private byte[] bytes = null;

    /** Binary image type. */
    private Type type = Type.NONE;

    /** Actual image width, or @c - if image width is unknown. */
    private int width = 0;

    /** Actual image height, or @c 0 if image height ts unknown. */
    private int height = 0;

    /** Original image type (for reporting purposes only). */
    private final Type origType;

    /** Original image width (f)or reporting purposes only). */
    private final int origWidth;

    /** Original image height for reporting purposes only). */
    private final int origHeight;

    /** List of frames to be added to image. */
    private final List<Rect> frames = new ArrayList<>();

    /** List of text items to be added to image. */
    private final List<TextItem> textItems = new ArrayList<>();

    /** List of operations performed on image. */
    private final List<String> processList = new ArrayList<>();

    /** Image timestamp */
    private final AtomicLong timestamp = new AtomicLong(0);
    
    /** Image meta data. */
    private final Map<String, Object> metadata = new LinkedHashMap<>();

    /** Last error message. */
    private String lastError = null;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new Image instance that does not hold any image.
     *
     * All member variables declared @c final are initialized with blank values. The other
     * member variables retain their already blank value.
     */
    public Image()
    {
        this.origType = Type.NONE;
        this.origWidth = 0;
        this.origHeight = 0;
    }

    /**
     * Constructs a new Image instance from an existing instance.
     * 
     * @param   image           Image instance to copy
     */
    public Image(Image image)
    {
        if ((image == null) || (image.hasImage() == false))
        {
            this.origType = Type.NONE;
            this.origWidth = 0;
            this.origHeight = 0;
            lastError = "image data is not available";
        }
        else
        {
            this.bitmap = (image.bitmap != null) ? Bitmap.createBitmap(image.bitmap) : null;
            this.bytes = (image.bytes != null) ? bytes.clone() : null;
            this.type = image.type;
            this.width = image.width;
            this.height = image.height;
            this.origType = image.origType;
            this.origWidth = image.width;
            this.origHeight = image.height;
            MapUtils.copyData(image.metadata, this.metadata);
            this.timestamp.set(timestamp.get());
        }
    }

    /**
     * Constructs a new Image instance from bitmap image data.
     *
     * The original bitmap data is copied so it can be safely destroyed after the Image instance has
     * been created.
     *
     * @param   bitmap          Android SDK @c Bitmap instance from which to construct image
     */
    public Image(Bitmap bitmap)
    {
        Type origType = Type.NONE;
        if ((bitmap == null) || (bitmap.isRecycled())) lastError = "bitmap data is not available";
        else
        {
            origType = Type.BITMAP;
            storeBitmapData(Bitmap.createBitmap(bitmap));
        }

        this.origType = origType;
        this.origWidth = this.width;
        this.origHeight = this.height;
    }

    /**
     * Constructs a new Image instance from the specified bitmap resource.
     *
     * If the bitmap data could not be retrieved from the resource, all member variables retain
     * their already blank value.
     *
     * @param   resources       application resources
     * @param   id              id of resource to create image from
     */
    public Image(Resources resources, int id)
    {
        Type origType = Type.NONE;
        bitmap = BitmapFactory.decodeResource(resources, id);
        if ((bitmap == null) || (bitmap.isRecycled())) lastError = "bitmap image data is not available";
        else
        {
            origType = Type.BITMAP;
            storeBitmapData(Bitmap.createBitmap(bitmap));
        }

        this.origType = origType;
        this.origWidth = this.width;
        this.origHeight = this.height;
    }

    /**
     * Constructs a new Image instance from binary image data and specified image size.
     *
     * The binary image data, image width and image height are copied to member variables. The
     * following image types are supported by this constructor:
     * - @e RGB888
     * - @e RGB565
     * - @e GRAY8
     * - @e YUV with @e NV21 encoding
     * - @e YUV with @e RGB565 encoding
     * - @e YUV with @e YUY2 encoding
     * - @e RAW
     *
     * The original byte array is cloned so it can be safely destroyed after the Image instance
     * has been created.
     *
     * @param   bytes           binary image data
     * @param   type            image type
     * @param   width           image width, or 0 if unknown
     * @param   height          image height, or 0 if unknown
     */
    public Image(byte[] bytes, Type type, int width, int height)
    {
        if ((bytes == null) || (bytes.length == 0) || (width == 0) || (height == 0)) this.lastError = "binary image data is not available";
        else storeBinaryData(bytes.clone(), type, width, height);

        this.origType = type;
        this.origWidth = this.width;
        this.origHeight = this.height;
    }

    /**
     * Constructs a new Image with instance from binary image data.
     *
     * The size and of the image is determined from the image data. If the size can not be 
     * determined all member variables retain their initial values. This following image types are 
     * supported by this constructor:
     * - @e JPEG
     * - @e PNG
     *
     * The original byte array is cloned so it can be safely destroyed after the Image instance
     * has been created.
     *
     * @param   bytes           binary image data
     * @param   type            image type
     */
    public Image(byte[] bytes, Type type)
    {
        if ((bytes == null) || (bytes.length == 0)) lastError = "binary image data is not available";
        else
        {
            if ((type != Type.JPEG) && (type != Type.PNG)) lastError = "unsupported image type " + type.name();
            else
            {
                Rect rect = getSize(type, bytes);
                if (rect != null) storeBinaryData(bytes.clone(), type, rect.width(), rect.height());
                else lastError = "failed to determine image size";
            }
        }

        origType = this.type;
        origWidth = this.width;
        origHeight = this.height;
    }

    /**
     * Constructs a new Image instance from a @e YUV image object.
     *
     * The binary image data, width and height are copied to member variables. Only @e NV21 and
     * @e RGB565 encoding is currently supported.
     *
     * The @e YUV binary image data is cloned so the original image can be safely destroyed after
     * the Image instance has been created.
     *
     * @param   yuvImage        Android @c YuvImage instance from which to construct image
     */
    public Image(YuvImage yuvImage)
    {
        if (yuvImage == null) lastError = "YUV image object is not available";
        else
        {
            Type type = Type.fromImageFormat(yuvImage.getYuvFormat());
            if (type == Type.NONE) lastError = "unsupported YUV image encoding " + yuvImage.getYuvFormat();
            else storeBinaryData(yuvImage.getYuvData().clone(), type, yuvImage.getWidth(), yuvImage.getHeight());
        }

        origType = this.type;
        origWidth = this.width;
        origHeight = this.height;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Factory method for creating image from byte array.
     *
     * A new Image instance is created and the timestamp and metadata is added.
     *
     * @param   bytes           binary image data
     * @param   type            image type
     * @param   width           image width, or 0 if unknown
     * @param   height          image height, or 0 if unknown
     * @param   timestamp       image timestamp, or 0 if unknown
     * @param   metadata        optional image metadata
     *
     * @return  created Image instance
     */
    @NonNull
    public static Image create(byte[] bytes, Image.Type type, int width, int height, long timestamp, Map<String, Object> metadata)
    {
        Image image = new Image(bytes, type, width, height);
        if (image.hasImage())
        {
            if (timestamp == 0) timestamp = System.currentTimeMillis();
            if (metadata == null) metadata = new HashMap<>();
            image.setData(timestamp, metadata);
        }
        return image;
    }

    /**
     * Returns @c true if this Image instance contains valid image data.
     *
     * @return  @c true if Image instance contains image, @c false if it is empty
     */
    public boolean hasImage()
    {
        return ((bitmap != null) || ((type != Type.NONE) && ((bytes != null) && (bytes.length > 0))));
    }

    /**
     * Returns the image as bitmap data.
     *
     * If bitmap data is available that data s returned, if not binaryToBitmap() is called to
     * convert the binary image data to bitmap data.
     *
     * @return  Jave @c Bitmap instance, or @c null if image is not available or conversion failed
     */
    @Nullable
    public Bitmap getBitmap()
    {
        // Return null if image data is not available.
        if (hasImage() == false) return nullBitmap("image data is not available");

        // Return bitmap data if already available.
        if (this.bitmap != null) return this.bitmap;

        // Convert binary data to bitmap data.
        return binaryToBitmap(this.bytes, this.type, this.width, this.height);
    }

    /**
     * Returns the image as binary data.

     * If binary data is available that data is returned, if not bitmapToBinary() is called to 
     * convert the bitmap image data to binary data.
     *
     * @return  byte array, or @c null if image is not available or conversion failed
     */
    @Nullable
    public byte[] getBytes()
    {
        // Return null if image data is not available.
        if (hasImage() == false) return nullBytes("image data is not available");

        // Return binary data if already available.
        if (this.bytes != null) return this.bytes;

        // Convert bitmap data to binary data of default type.
        return bitmapToBinary(this.bitmap, DEFAULT_BINARY_DATA_TYPE, DEFAULT_JPEG_QUALITY);
    }

    /**
     * Returns the binary image data type.
     *
     * @return  Type enumerator value specifying binary image data type
     */
    public Type getType() { return type; }

    /**
     * Returns the image as binary data of specified type.
     *
     * If binary data is available and the requested image type matches the current image type that
     * data is returned. If binary data is not available or the requested type differs from the
     * current type, but bitmap data is available, bitmapToBinary() is called to convert the bitmap
     * data to the requested type. In all other cases binaryToBinary() is called to convert between
     * binary image data types.
     *
     * @param   type            binary image type to return
     * @param   option          optional conversion option(s)
     *
     * @return  byte array, or @c null if image is not available or conversion failed
     */
    @Nullable
    public byte[] getBytes(Type type, Object option)
    {
        // Return null if image data is not available.
        if (hasImage() == false) return nullBytes("image data is not available");

        // Return binary data if requested type matches image type.
        if ((this.bytes != null) && (type == this.type)) return this.bytes;

        // If bitmap is available convert bitmap data to binary data.
        if (this.bitmap != null) return bitmapToBinary(this.bitmap, type, option);

        // Convert between different binary types.
        return binaryToBinary(this.bytes, this.type, this.width, this.height, type, option);
    }

    /**
     * Returns the image dimensions.
     *
     * @return  Java @c Rect instance specifying image dimensions
     */
    @Nullable
    public Rect getRect()
    {
        if (hasImage() == false)
        {
            lastError = "image data is not available";
            return null;
        }
        
        Rect rect = new Rect();
        rect.left = 0;
        rect.top = 0;
        rect.right = width-1;
        rect.bottom = height-1;
        return rect;
    }

    /**
     * Returns a mutable copy of the current Image instance.
     *
     * @return  copy of current Image instance, or empty image if copy fails
     */
    @NonNull
    public Image copy() { return new Image(this); }

    /**
     * Convert image to specified image type.
     *
     * If the requested image type is the same as the current type there is nothing to do. If the
     * image type is @e BITMAP getBitmap() is called to convert the image data to bitmap data if
     * necessary, and storeBitmapData() is called to store the bitmap data in a member variable. For
     * binary image types getBytes() is called to convert the image data to binary data if
     * necessary, and storeBinaryData() is called to store the binary data in member variables.
     *
     * @param   type            type to which to convert image
     * @param   option          optional conversion option(s)
     *
     * @return  this @c Image instance on success, @c null on failure 
     */
    @Nullable
    public Image convert(Type type, Object option)
    {
        // Return null if image data is not available.
        if (hasImage() == false) return nullImage("image data is not available");

        // If the image already is of the specified type there is nothing to do.
        if (type == this.type) return this;

        if (type == Type.BITMAP)
        {
            Bitmap bitmap = getBitmap();
            if (bitmap != null) storeBitmapData(bitmap);
            else return null;
        }
        else
        {
            byte[] bytes = getBytes(type, option);
            if (bytes != null) storeBinaryData(bytes, type, this.width, this.height);
            else return null;
        }
        return this;
    }

    /**
     * Resizes the image.
     *
     * If the image is an @e YUV image with @e NV21 encoding it is resized using a dedicated method.
     * For other image types the bitmap data is resized if bitmap data is available. If bitmap data
     * is not available an intermediate bitmap is created and resized. The resized bitmap is
     * converted back to the current image type if necessary (i.e. if current type is not bitmap).
     * Resizing is only supported for the following image types:
     * = @e BITMAP
     * - @e RGB888
     * - @e RGB565
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     * - @e YUV_NV21
     * - @e YUV_YUY2
     *
     * @param   newWidth        requested image width
     * @param   newHeight       requested image height
     *
     * If @p width (@p height) equals 0, the image will be resized to he specified height (width)
     * without changing the aspect ratio. If both are 0 the image will not be resized.
     *
     * @return  this @c Image instance containing resized image data on success, @c null on failure
     */
    @Nullable
    public Image resize(int newWidth, int newHeight)
    {
        // Return null if image data is not available.
        if (hasImage() == false) return nullImage("image data is not available");

        // Return current image if width and heigh are both 0 or equal to current width and height.
        if (((newWidth == 0) && (newHeight == 0)) || ((newWidth == width) && (newHeight == height))) return this;

        Bitmap newBitmap = null;
        byte[] newBytes = null;

        if (type == Type.YUV_NV21)
        {
            // Use dedicated method fur resizing YUV-NV21 image.
            newBytes = resizeNv21Image(bytes, width, height, newWidth, newHeight);
            if (newBytes == null) return nullImage("failed to resize YUV image");
        }
        else
        {
            if ((bitmap != null) && (origType == Type.BITMAP)) newBitmap = resizeBitmap(bitmap, newWidth, newHeight);
            else
            {
                // Return null if resizing is not supported for image type.
                if ((type != Type.RGB888) && (type != Type.RGB565) && (type != Type.PNG) && (type != Type.JPEG) && (type != Type.WEBP) && (type != Type.YUV_YUY2))
                    return nullImage("resizing " + type.name() + " images is not supported");

                // Resize stored or intermediate bitmap..
                Bitmap origBitmap = (bitmap != null) ? bitmap : binaryToBitmap(bytes, type, width, height);
                if (origBitmap == null) return null;
                newBitmap = resizeBitmap(origBitmap, newWidth, newHeight);

                // Convert image back to current type.
                newBytes = bitmapToBinary(newBitmap, type, 100);
                if (newBytes == null) return null;
            }
        }

        // Store or clear bitmap data and binary data.
        storeBitmapData(newBitmap);
        storeBinaryData(newBytes, type, newWidth, newHeight);
        processList.add("resize");
        return this;
    }

    /**
     * Horizontally mirrors the image.
     *
     * Horizontal mirroring is only supported for the following image types:
     * - @e BITMAP
     * - @e RGB888
     * - @e RGB565
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     * - @e YUV_NV21
     * - @e YUV_YUY2
     *
     * @return  this @c Image instance containing mirrored image data on success, @c null on failure
     */
    @Nullable
    public Image mirrorHorizontal()
    {
        Image image = mirror(true);
        if (image != null) image.processList.add("hmirror");
        return image;
    }

    /**
     * Vertically mirrors the image.
     *
     * Vertical mirroring is only supported for the following image types:
     * - @e BITMAP
     * - @e RGB888
     * - @e RGB565
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     * - @e YUV_NV21
     * - @e YUV_YUY2
     *
     * @return  this @c Image instance containing mirrored image data on success, @c null on failure
     */
    @Nullable
    public Image mirrorVertical()
    {
        Image image = mirror(false);
        if (image != null) image.processList.add("vmirror");
        return image;
    }

    /**
     * Adds a frame to the list of frames to be drawn on the bitmap.
     *
     * A new @c Rect instance containing the frame coordinates is added to the list of frames to be
     * drawn. The coordinates are adjusted to fit the image.
     *
     * @param   left            left position of frame
     * @param   top             top position of frame
     * @param   right           right position of frame
     * @param   bottom          bottom position of frame
     */
    public void addFrame(int left, int top, int right, int bottom)
    {
        Rect rect = new Rect();
        rect.left = Math.max(0, left);
        rect.top = Math.max(0, top);
        rect.right = Math.min(width, right);
        rect.bottom = Math.min(height, bottom);
        frames.add(rect);
    }

    /**
     * Draws a single frame in the image.
     *
     * @param   left            left of frame
     * @param   top             top of frame
     * @param   right           right of frame
     * @param   bottom          bottom of frame
     * @param   width           line width
     * @param   color           line color
     *
     * @return  this @c Image instance containing updated image data on success, @c null on failure
     */
    @Nullable
    public Image drawFrame(int left, int top, int right, int bottom, int width, int color)
    {
        addFrame(left, top, right, bottom);
        return drawFrames(width, color);
    }

    /**
     * Draws the frames specified in the frame list in the image.
     *
     * If bitmap data is not available an intermediate bitmap is created and frames are added. The
     * updated bitmap is coverted back to the current image type if necessary (i.e. if current type
     * is not bitmap). Adding frames is only supported for the following image types:
     * = @e BITMAP
     * - @e RGB888
     * - @e RGB565
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     * - @e YUV_NV21
     * - @e YUV_YUY2
     *
     * @param   lineColor       line color
     * @param   lineWidth       line width
     *
     * @return  this @c Image instance containing updated image data on success, @c null on failure
     */
    @Nullable
    public Image drawFrames(int lineColor, int lineWidth)
    {
        // Return original image if list contains no frames.
        if (frames.isEmpty()) return this;

        // Return null if image data is not available.
        if (hasImage() == false) return nullImage("image data is not available");

        Bitmap newBitmap;
        byte[] newBytes = null;

        if ((bitmap != null) && (origType == Type.BITMAP))
        {
            newBitmap = drawFrames(bitmap, lineColor, lineWidth);
            if (newBitmap == null) return nullImage("failed to add frames in bitmap image");
        }
        else
        {
            if ((type != Type.RGB888) && (type != Type.RGB565) && (type != Type.PNG) && (type != Type.JPEG) && (type != Type.WEBP) && (type != Type.YUV_NV21) && (type != Type.YUV_YUY2))
                return nullImage("adding frames in " + type.name() + " images is not supported");

            // Add frames to stored or intermediate bitmap.
            Bitmap origBitmap = (bitmap != null) ? bitmap : binaryToBitmap(bytes, type, width, height);
            if (origBitmap == null) return null;
            newBitmap = drawFrames(origBitmap, lineColor, lineWidth);
            if (newBitmap == null) return nullImage("failed to add frames in " + type.name() + " image");

            // Convert intermediate bitmap to current type.
            newBytes = bitmapToBinary(newBitmap, type, 100);
            if (newBytes == null) return null;
        }

        // Store or clear bitmap data and binary data.
        storeBitmapData(newBitmap);
        storeBinaryData(newBytes, type, width, height);

        processList.add("frames");
        return this;
    }

    /**
     * Adds a text item to the list of text items to be drawn on the bitmap.
     *
     * @param   text            text to be added to bitmap
     * @param   left            left position of text
     * @param   bottom          top position of text
     * @param   fontName        font name
     * @param   fontWeight      font weight
     * @param   fontSize        font size
     * @param   fontColor       font color
     *
     * The values of @p left, @p bottom and @p fontSize or in units of 1/1000th of the image size,
     * i.e. the values are for an image of 1000 pixels wide, so the absolute sizes are corrected for
     * the actual image width. Positive values of @p left and @p bottom are computed relative to the
     * left or top of the image, negative values are relative to the right or bottom of the image.
     */
    public void addText(String text, int left, int bottom, String fontName, int fontWeight, int fontSize, int fontColor)
    {
        float scale = width/1000f;
        float pixelLeft = scale*left;
        float pixelBottom = scale*bottom;
        pixelLeft = Math.max(0, Math.min(width, (pixelLeft >= 0) ? pixelLeft : width + pixelLeft - 1));
        pixelBottom = Math.max(0, Math.min(height, (pixelBottom >= 0) ? pixelBottom : height+pixelBottom-1));
        textItems.add(new TextItem(text, pixelLeft, pixelBottom, fontName, fontWeight, scale*fontSize, fontColor));
    }

    /**
     * Draws a single text item in the image.
     *
     * @param   text            text to be added to bitmap
     * @param   left            left position of text
     * @param   bottom          top position of text
     * @param   fontName        font name
     * @param   fontWeight      font weight
     * @param   fontSize        font size
     * @param   fontColor       font color
     *
     * @return  this @c Image instance containing updated image data on success, @c null on failure
     */
    @Nullable
    public Image drawText(String text, int left, int bottom, String fontName, int fontWeight, int fontSize, int fontColor)
    {
        addText(text, left, bottom, fontName, fontWeight, fontSize, fontColor);
        return drawText();
    }

    /**
     * Draws the text items specified in the text item list in the image.
     *
     * If bitmap data is not available an intermediate bitmap is created and text items are added.
     * The updated bitmap is converted back to the current image type if necessary (i.e. if current
     * type is not bitmap). Adding frames is only supported for the following image types:
     * - @e BITMAP
     * - @e RGB888
     * - @e RGB565
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     * - @e YUV_NV21
     * - @e YUV_YUY2
     *
     * @return  this @c Image instance containing updated image data on success, @c null on failure
     */
    @Nullable
    public Image drawText()
    {
        // Return original image if list contains no text items.
        if (textItems.isEmpty()) return this;

        // Return null if image data is not available.
        if (hasImage() == false) return nullImage("image data is not available");

        Bitmap newBitmap;
        byte[] newBytes = null;

        if ((bitmap != null) && (origType == Type.BITMAP))
        {
            newBitmap = drawText(bitmap);
            if (newBitmap == null) return nullImage("failed to add text items in bitmap image");
        }
        else
        {
            if ((type != Type.RGB888) && (type != Type.RGB565) && (type != Type.PNG) && (type != Type.JPEG) && (type != Type.WEBP) && (type != Type.YUV_NV21) && (type != Type.YUV_YUY2))
                return nullImage("adding text in " + type.name() + " images is not supported");

            // Add frames to stored or intermediate bitmap.
            Bitmap origBitmap = (bitmap != null) ? bitmap : binaryToBitmap(bytes, type, width, height);
            if (origBitmap == null) return null;
            newBitmap = drawText(origBitmap);
            if (newBitmap == null) return nullImage("failed to add text in " + type.name() + " image");

            // Convert intermediate bitmap to current type.
            newBytes = bitmapToBinary(newBitmap, type, 100);
            if (newBytes == null) return null;
        }

        // Store or clear bitmap data and binary data.
        storeBitmapData(newBitmap);
        storeBinaryData(newBytes, type, width, height);

        processList.add("text");
        return this;
    }

    /**
     * Saves image to file.
     *
     * Saving images is only supported for the following file types:
     * - @c RAW
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     *
     * @param   dir             Java @c File instance specifying directory in which to save file
     * @param   fileName        file name without extension
     * @param   type            image type to save
     * @param   option          optional conversion option(s)
     *
     * @return  this Image instance on success, @c null on failure
     */
    @Nullable
    public Image save(File dir, String fileName, @NonNull Type type, Object option)
    {
        // Get file extension matching specified image type.
        String fileExtension = type.toFileExtension();
        if (fileExtension == null) return nullImage("saving " + type.name() + " images to file is not supported");

        // Get binary image data.
        byte[] bytes = getBytes(type, option);
        if (bytes == null) return nullImage(type.name() + " image data is not available");

        // Validate directory and file name.
        if ((dir == null) || ((dir.exists() == false) && (dir.mkdirs() == false)) || (fileName == null) || (fileName.trim().isEmpty()))
            return nullImage("specified path is invalid");

        // Save file.
        File imageFile = new File(dir, fileName + "." + fileExtension);
        try (FileOutputStream out = new FileOutputStream(imageFile))
        {
            out.write(bytes);
            out.flush();
            processList.add("save");
            return this;
        }
        catch (Exception e)
        {
            return nullImage("failed to save image to file");
        }
    }

    /**
     * Sets an image timestamp and overwrites existing image metadata,
     *
     * @param   timestamp       image timestamp as @c long
     * @param   metadata        Java @c map instance containing new metadata
     */
    public void setData(long timestamp, Map<String, Object> metadata)
    {
        setTimestamp(timestamp);
        setMetaData(metadata);
    }
    
    /**
     * Sets an image timestamp.
     *
     * @param   timestamp       image timestamp as @c long
     */
    public void setTimestamp(long timestamp)
    {
        this.timestamp.set(timestamp);
    }

    /**
     * Overwrites existing image metadata with the metadata in specified map.
     *
     * @param   metadata        Java @c map instance containing new metadata
     */
    public void setMetaData(Map<String, Object> metadata)
    {
        MapUtils.copyData(metadata, this.metadata);
    }

    /**
     * Returns the image timestamp.
     * 
     * @return  image timestamp as @c long
     */
    public long getTimestamp()
    {
        return timestamp.get();
    }

    /**
     * Returns image metadata.
     *
     * The image details are added to the available metadata.
     *
     * @return  Java @c Map instance containing image meta data
     */
    @NonNull
    public Map<String, Object> getMetaData()
    {
        Map<String, Object> metadata = new LinkedHashMap<>();
        MapUtils.copyData(this.metadata, metadata);
        if (type != Type.NONE)
        {
            metadata.put("Image-Type", type.name());
            metadata.put("Image-Width", width);
            metadata.put("Image-Height", height);
            if (bitmap != null) metadata.put("Image-Size", bitmap.getByteCount());
            else metadata.put("Image-Size", bytes.length);
            metadata.put("Image-OrigType", origType);
            metadata.put("Image-OrigWidth", (origWidth != 0) ? origWidth : "<unknown>");
            metadata.put("Image-OrigHeight", (origHeight != 0) ? origHeight : "<unknown>");
            if (processList.isEmpty() == false) metadata.put("Image-Processed", String.join(",", processList));
        }
        return metadata;
    }

    /**
     * Returns the last error message.
     *
     * @return  string containing last message
     */
    @Nullable
    public String getError() { return lastError; }

    /**
     * Mirrors a @e JPEG image.
     *
     * A new instance of this class is created from the @e JPEG image data, the image is mirrored
     * and the mirrored image is compressed back to @e JPEG image data.
     *
     * @param   bytes           JPEG image data
     *
     * @return  byte array containing mirrored @e JPEG data, or original data if mirroring fails
     */
    @Nullable
    public static byte[] mirrorJpeg(byte[] bytes)
    {
        Image image = new Image(bytes, Type.JPEG).mirrorHorizontal();
        return (image != null) ? image.getBytes() : null;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Converts stored binary image data to bitmap data.
     *
     * If bitmap data is already available the function just returns the @c bitmap member variable.
     * In all other cases the method for converting the binary image data to bitmap data is called.
     * Conversion of the following binary image data types is supported:
     * - @e RGB888
     * - @e RGB565
     * - @e GRAY8
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     * - @e YUV_NV21
     * - @e YUV_RGB565
     * - @e YUV_YUY2
     *
     * @param   bytes           byte array containing binary image data to convert
     * @param   type            type of image to convert
     * @param   width           width of image to convert
     * @param   height          height of image to convert
     *
     * @return  Java @c Bitmap instance, or @c null if data can not be converted
     */
    @Nullable
    private Bitmap binaryToBitmap(byte[] bytes, @NonNull Type type, int width, int height)
    {
        switch (type)
        {
            case PNG:
            case JPEG:
            case WEBP:
                return compressedToBitmap(bytes, type);
            case RGB888:
                return rgb888ToBitmap(bytes, width, height);
            case RGB565:
                return rgb565ToBitmap(bytes, width, height);
            case GRAY8:
                return gray8ToBitmap(bytes, width, height);
            case YUV_NV21:
                return nv21ToBitmap(bytes, width, height);
            case YUV_YUY2:
                return yuy2ToBitmap(bytes, width, height);
            default:
                return nullBitmap("converting from " + type.name() + " image data to bitmap image data is not supported");
        }
    }

    /**
     * Converts binary @e RGB888 image data to bitmap.
     *
     * The red, green and blue values of each pixel are stored as three 8-bit @e RGB888 bytes in the
     * byte array, so conversion to a bitmap is trivial: a new four-byte value representing a pixel
     * is created by assigning a fixed value of 255 to the alpha channel and copying the 'red,
     * 'green' and 'blue' bytes.
     *
     * @param   bytes           byte array containing binary image data to convert
     * @param   width           width of image to convert
     * @param   height          height of image to convert
     *
     * @return  Java @c Bitmap instance, or @c null if conversion fails
     */
    @Nullable
    private Bitmap rgb888ToBitmap(byte[] bytes, int width, int height)
    {
        // Return null if binary image data is invalid.
        if ((bytes == null) || (bytes.length == 0)) return nullBitmap("binary data is not valid RGB image data");

        // Convert pixels in RGB image.
        int pixelCount = Math.min(width*height, bytes.length/3);
        int[] pixels = new int[pixelCount];
        int byteIndex = 0;
        for (int i=0; i<pixelCount; i++)
        {
            int r8 = bytes[byteIndex++] & 0xFF;
            int g8 = bytes[byteIndex++] & 0xFF;
            int b8 = bytes[byteIndex++] & 0xFF;
            pixels[i] = Color.argb(255, r8, g8, b8);
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        if (bitmap == null) return nullBitmap("failed to convert RGB888 image data to bitmap image data");

        return bitmap;
    }

    /**
     * Converts bitmap to binary RGB565 image data.
     *
     * The red, green and blue values of each pixel are stored as one 16-bit @e RGB565 value in the
     * byte array as
     * - byte n: bits <em>r4 r3 r2 r1 r0 g5 g4 g3</em>
     * - byte n+1: bits <em>g2 g1 g0 b4 b3 b2 b1 b0</em>
     *
     * For little-endian byte orders the oder of the two bytes is swapped.
     *
     * The red, blue and green value are extracted from this 16-bit value and converted to eight-bit
     * bytes. A new four-byte value representing a pixel is created by assigning a fixed value of
     * 255 to the alpha channel and copying the 'red, 'green' and 'blue' bytes.
     *
     * When encoding a pixel @e RGB565 the lowest three 'red' and 'blue' bits and the lowest two
     * 'green' bits were lost. Instead of leaving the lowest bits 0 when converting back to eight
     * bit values, the five/six bit color values are stretched to eight bits to obtain a better
     * visual result.
     *
     * @param   bytes           byte array containing binary image data to convert
     * @param   width           width of image to convert
     * @param   height          height of image to convert
     *
     * @return  Java @c Bitmap instance, or @c null if conversion fails
     */
     private Bitmap rgb565ToBitmap(byte[] bytes, int width, int height)
    {
        int pixelCount = bytes.length / 2;
        int[] pixels = new int[pixelCount];

        int byteIndex = 0;
        int pixelIndex = 0;
        while (byteIndex< bytes.length-1)
        {
            int value;
            if (RGB565_LITLLE_ENDIAN) value = (bytes[byteIndex] & 0xFF) | ((bytes[byteIndex + 1] & 0xFF) << 8);
            else value = ((bytes[byteIndex] & 0xFF) << 8) | (bytes[byteIndex + 1] & 0xFF);
            byteIndex += 2;

            // Extract RGB565 values.
            int r5 = (value >> 11) & 0x1F;
            int g6 = (value >> 5) & 0x3F;
            int b5 = value & 0x1F;

            // Convert to RGB888 values.
            int r8 = (r5 << 3) | (r5 >> 2);
            int g8 = (g6 << 2) | (g6 >> 4);
            int b8 = (b5 << 3) | (b5 >> 2);
            pixels[pixelIndex++] = (0xFF << 24) | ((r8 & 0xFF) << 16) | ((g8 & 0xFF) << 8) | (b8 & 0xFF);
    }
    Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    if (bitmap == null) return nullBitmap("failed to convert RGB565 image data to bitmap image data");

    return bitmap;
}
    /**
     * Converts stored binary @e GRAY8 image data to bitmap data.
     *
     * Each byte representing a @e GRAY8 pixel is converted to an integer representing a bitmap
     * pixel by just copying the gray scale value to the red, blue and green @e RGB values.
     *
     * @param   bytes           byte array containing binary image data to convert
     * @param   width           width of image to convert
     * @param   height          height of image to convert
     *
     * @return  Java @c Bitmap instance, or @c null if conversion fails
     */
    private Bitmap gray8ToBitmap(byte[] bytes, int width, int height)
    {
        // Return null if binary image data is invalid.
        if ((bytes == null) || (bytes.length == 0)) return nullBitmap("binary data is not valid GRAY8 image data");

        // Convert pixels in GRRAY8 image.
        int pixelCount = Math.min(width*height, bytes.length);
        int[] pixels = new int[pixelCount];
        for (int i=0; i<pixelCount; i++)
        {
            int gray = bytes[i] & 0xFF;
            pixels[i] = Color.argb(255, gray, gray, gray);
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        if (bitmap == null) return nullBitmap("failed to convert GRAY8 image data to bitmap image data");

        return bitmap;
    }

    /**
     * Converts stored binary @e PNG, @e JPEG or @e WEBP image data to bitmap data.
     *
     * The binary data is decoded directly using the native @c BitmapFactory.decodeByteArray()
     * method.
     *
     * @param   bytes           byte array containing binary image data to convert
     * @param   type            type of image to convert
     *
     * @return  Java @c Bitmap instance, or @c null if conversion fails
     */
    @Nullable
    private Bitmap compressedToBitmap(byte[] bytes, Type type)
    {
        // Return null if binary image data is invalid.
        if ((bytes == null) || (bytes.length == 0)) return nullBitmap("binary data is not valid " + type.name() + " image data");

        // Decode binary data.
        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
        if (bitmap == null) return nullBitmap("failed to convert " + type.name() + " image data to bitmap image data");

        return bitmap;
    }

    /**
     * Converts stored binary @e YUV-NV21 image data to bitmap data.
     *
     * @param   bytes           byte array containing binary image data to convert
     * @param   width           width of image to convert
     * @param   height          height of image to convert
     *
     * @return  Java @c Bitmap instance, or @c null if conversion fails
     */
    @Nullable
    private Bitmap nv21ToBitmap(byte[] bytes, int width, int height)
    {
        int pixelCount = width*height;
        int[] pixels = new int[pixelCount];

        for (int y=0; y<height; y++)
        {
            int yRow = y*width;
            int uvRow = pixelCount + (y >> 1)*width;

            for (int index = 0; index <width; index++)
            {
                int yValue = bytes[yRow + index] & 0xFF;
                int uvIndex = uvRow + (index & ~1);
                int v = bytes[uvIndex] & 0xFF;
                int u = bytes[uvIndex + 1] & 0xFF;

                pixels[yRow+ index] = yuvToColor(yValue, u, v);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        if (bitmap == null) return nullBitmap("failed to convert YUV-NV21 image data to bitmap image data");

        return bitmap;
    }

    /**
     * Converts stored binary @e YUV-YUY2 image data to bitmap data.
     *
     * Each quartet of bytes representing a pair of @e YUV-YUY2 pixels is converted to two integers
     * representing a pair of bitmap pixels.
     *
     * @param   bytes           byte array containing binary image data to convert
     * @param   width           width of image to convert
     * @param   height          height of image to convert
     *
     * @return  Java @c Bitmap instance, or @c null if conversion fails
     */
    private Bitmap yuy2ToBitmap(byte[] bytes, int width, int height)
    {
        // Return null if binary image data is invalid.
        if ((bytes == null) || (bytes.length == 0)) return nullBitmap("binary data is not valid YUV-YUY2 image data");

        int pixelCount = Math.min(width*height, bytes.length/2);
        int[] pixels = new int[pixelCount];
        int byteIndex = 0;
        int pixelIndex = 0;
        while (pixelIndex < pixelCount)
        {
            int y0 = bytes[byteIndex++] & 0xFF;
            int u = bytes[byteIndex++] & 0xFF;
            int y1 = bytes[byteIndex++] & 0xFF;
            int v = bytes[byteIndex++] & 0xFF;
            pixels[pixelIndex++] = yuvToColor(y0, u, v);
            pixels[pixelIndex++] = yuvToColor(y1, u, v);
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        if (bitmap == null) return nullBitmap("failed to convert YUV-YUY2 image data to bitmap image data");

        return bitmap;
    }

    /**
     * Converts @e YUV color to @e RGB colors.
     *
     * @param   y   luma value
     * @param   u   u-chrpma value
     * @param   v   v0chroma value
     *
     * @return  integer value representing @e RGB color
     *
     * The returned volor color just has the three bytes specifying the @e RGB values packed into a
     * single integer, with the highest byte unused, and the other three bytes (from hight to low)
     * representing the @e RGB values.
     */
    private int yuvToColor(int y, int u, int v)
    {
        int c = y - 16;
        int d = u - 128;
        int e = v - 128;
        if (c < 0) c = 0;

        byte r = toByte((298*c + 409*e + 128) >> 8);
        byte g = toByte((298*c - 100*d - 208*e + 128) >> 8);
        byte b = toByte((298*c + 516*d + 128) >> 8);

        return (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /**
     * Converts stored bitmap image data to binary data of specified type.
     *
     * The method for converting the bitmap data to the requested image type is called. Binary image
     * data already present is replaced by the compressed image data. Conversion to the following
     * binary image data types is supported:
     * - @e RGB888
     * - @e RGB565
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     *
     * @param   bitmap          Java @c Bitmap instance containing bitmap data to convert
     * @param   type            image type to which to convert
     * @param   option          optional conversion option(s)
     *
     * @return  non-empty byte array, or @c null if data can not be converted
     */
    @Nullable
    private byte[] bitmapToBinary(Bitmap bitmap, @NonNull Type type, Object option)
    {
        switch (type)
        {
            case RGB888:
                return bitmapToRgb888(bitmap);
            case RGB565:
                return bitmapToRgb565(bitmap);
            case PNG:
            case JPEG:
            case WEBP:
                return bitmapToCompressed(bitmap, type, option);
            case YUV_NV21:
                return bitmapToNv21(bitmap);
            case YUV_YUY2:
                return bitmapToYuy2(bitmap);
            default:
                return nullBytes("converting from " + type.name() + " image data to binary image data is not supported");
        }
    }

    /**
     * Converts bitmap to binary @e RGB888 image data.
     *
     * Each pixel in the bitmap is represented by a single four-byte integer with the bytes
     * representing (from highest to lowest byte) the alpha channel, the 'red' value, the 'green'
     * value and the 'blue' value. Conversion to @e RGB888 is trivial; the alpha channel is ignored,
     * the other three bytes are just copied to the byte array.
     *
     * @param   bitmap          Java @c Bitmap instance containing bitmap data to convert
     *
     * @return  non-empty byte array, or @c null if conversion fails
     */
    @Nullable
    private byte[] bitmapToRgb888(Bitmap bitmap)
    {
        // Return null if bitmap is not available.
        int[] pixels = getPixelsFromBitmap(bitmap);
        if (pixels == null) return null;

        // Convert each pixel in the array.
        int pixelCount = pixels.length;
        byte[] bytes = new byte[pixelCount*3];
        int byteCount = 0;
        for (int pixel : pixels)
        {
            bytes[byteCount++] = (byte)((pixel >> 16) & 0xFF);
            bytes[byteCount++] = (byte)((pixel >> 8) & 0xFF);
            bytes[byteCount++] = (byte)(pixel & 0xFF);
        }
        return bytes;
    }

    /**
     * Converts bitmap to binary @e RGB565 image data.
     *
     * Each pixel in the bitmap is represented by a single four-byte integer with the bytes
     * representing (from highest to lowest byte) the alpha channel, the 'red' value, the 'green'
     * value and the 'blue' value. To convert, the alpha channel is ignored and the the red, green
     * and blue values of each pixel are stored as one 16-bit @e RGB565 value in little-endian byte
     * order as
     * - byte n -  <em>g4 g3 g2 b7 b6 b5 b4 b3</em>
     * - byte n+1 - <em>r7 r6 r5 r4 r3 g7 g6 g5</em>
     *
     * This means the three lowest 'red' and 'blue' bits and the two lowest 'green' bits are lost,
     * so conversion back to bitmap can not be done without losing some color information.
     *
     * @param   bitmap          Java @c Bitmap instance containing bitmap data to convert
     *
     * @return  non-empty byte array, or @c null if conversion fails
     */
    @Nullable
    private byte[] bitmapToRgb565(Bitmap bitmap)
    {
        // Return null if bitmap is not available.
        int[] pixels = getPixelsFromBitmap(bitmap);
        if (pixels == null) return null;

        // Convert each pixel in the array.
        int pixelCount = pixels.length;
        byte[] bytes = new byte[pixelCount*2];
        int byteCount = 0;
        for (int pixel : pixels)
        {
            byte r = (byte)((pixel >> 16) & 0xFF);
            byte g = (byte)((pixel >> 8) & 0xFF);
            byte b = (byte)(pixel & 0xFF);
            int value = ((r >> 3) << 11) | ((g >> 2) << 5)  | (b >> 3);

            if (RGB565_LITLLE_ENDIAN)
            {
                bytes[byteCount++] = (byte)(value & 0xFF);
                bytes[byteCount++] = (byte)((value >> 8) & 0xFF);
            }
            else
            {
                bytes[byteCount++] = (byte)((value >> 8) & 0xFF);
                bytes[byteCount++] = (byte)(value & 0xFF);
            }
        }
        return bytes;
    }

    /**
     * Converts bitmap to binary @c YUV-NV21 image data.
     *
     * @param   bitmap          Java @c Bitmap instance containing bitmap data to convert
     *
     * @return  non-empty byte array, or @c null if conversion fails
     */
    @NonNull
    private byte[] bitmapToNv21(@NonNull Bitmap bitmap)
    {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int pixelCount = width*height;
        int[] pixels = new int[pixelCount];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        byte[] bytes = new byte[pixelCount + (pixelCount/ 2)];

        int yIndex = 0;
        int uvIndex = pixelCount;
        for (int j=0; j<height; j++)
        {
            for (int i=0; i<width; i++)
            {
                int pixel = pixels[j*width+i];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                int y = (( 66*r + 129*g +  25*b + 128) >> 8) + 16;
                int u = ((-38*r -  74*g + 112*b + 128) >> 8) + 128;
                int v = ((112*r -  94*g -  18*b + 128) >> 8) + 128;
                bytes[yIndex++] = toByte(y);
                if ((j % 2 == 0) && (i % 2 == 0))
                {
                    bytes[uvIndex++] = toByte(v);
                    bytes[uvIndex++] = toByte(u);
                }
            }
        }

        return bytes;
    }

    /**
     * Converts bitmap to binary YUV-YUY2 image data.
     *
     * Each pair of integers representing two bitmap pixels is converted to a quartet of bytes
     * representing four @e YUV-YUY2 pixels.
     *
     * @param   bitmap          Java @c Bitmap instance containing bitmap data to convert
     *
     * @return  non-empty byte array, or @c null if conversion fails
     */
    private byte[] bitmapToYuy2(Bitmap bitmap)
    {
        // Return null if bitmap is not available.
        if (bitmap == null) return nullBytes("bitmap image data is not valid");

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width*height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        byte[] bytes = new byte[width*height*2];
        int outIndex = 0;

        for (int y=0; y<height; y++)
        {
            int row = y*width;
            for (int x=0; x<width; x+=2)
            {
                int p0 = pixels[row + x];
                int p1 = pixels[row + Math.min(x+1, width-1)];

                int r0 = (p0 >> 16) & 0xFF;
                int g0 = (p0 >> 8) & 0xFF;
                int b0 = p0 & 0xFF;

                int r1 = (p1 >> 16) & 0xFF;
                int g1 = (p1 >> 8) & 0xFF;
                int b1 = p1 & 0xFF;

                int y0 = toByte((( 66*r0 + 129*g0 +  25*b0 + 128) >> 8) + 16);
                int u0 = toByte(((-38*r0 -  74*g0 + 112*b0 + 128) >> 8) + 128);
                int v0 = toByte(((112*r0 -  94*g0 -  18*b0 + 128) >> 8) + 128);

                int y1 = toByte((( 66*r1 + 129*g1 +  25*b1 + 128) >> 8) + 16);
                int u1 = toByte(((-38*r1 -  74*g1 + 112*b1 + 128) >> 8) + 128);
                int v1 = toByte(((112*r1 -  94*g1 -  18*b1 + 128) >> 8) + 128);

                int u = (u0 + u1) / 2;
                int v = (v0 + v1) / 2;

                bytes[outIndex++] = (byte)y0;
                bytes[outIndex++] = (byte)u;
                bytes[outIndex++] = (byte)y1;
                bytes[outIndex++] = (byte)v;
            }
        }

        return bytes;
    }

    /**
     * Compresses bitmap data to specified image type.
     *
     * The bimap data is compressed using the native @c Bitmap.compress() method.
     *
     * @param   bitmap          Java @c Bitmap instance containing bitmap data to convert
     * @param   type            image type to which to convert
     * @param   option          options conversion option(s)
     *
     * @return  non-empty byte array, or @c null if conversion fails
     */
    @Nullable
    private byte[] bitmapToCompressed(Bitmap bitmap, Type type, Object option)
    {
        // Return null if bitmap data is not available.
        if (bitmap == null) return nullBytes("bitmap image data is not valid");

        // Java image compression format matching specified type.
        Bitmap.CompressFormat format = type.toCompressFormat();
        if (format == null) return nullBytes("conversion of bitmap image data to " + type.name() + " image data is not supported");

        // Compress bitmap to requested format.
        int quality = (option instanceof Integer)
            ? Math.max(MIN_JPEG_QUALITY, Math.min(MAX_JPEG_QUALITY, ((Integer)option)))
            : DEFAULT_JPEG_QUALITY;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        boolean result = bitmap.compress(format, quality, stream);
        bytes = (result) ? stream.toByteArray() : null;

        if ((bytes == null) || (bytes.length == 0)) return nullBytes("conversion of bitmap image data to " + type.name() + " image data failed");

        return bytes;
    }

    /**
     * Converts between different binary data types.
     *
     * @param   bytes           byte array containing binary image data to convert
     * @param   type            type of image to convert
     * @param   width           width of image to convert
     * @param   height          height of image to convert
     * @param   destType        image type to which to convert
     * @param   option          optional conversion option(s)
     *
     * @return  non-empty byte array, or @c null if conversion fails
     */
    @Nullable
    private byte[] binaryToBinary(byte[] bytes, Type type, int width, int height, Type destType, Object option)
    {
        // Return null if binary image data is invalid.
        if ((bytes == null) || (bytes.length == 0))
            return nullBytes("binary data is not valid " + type + " image data");

        // If original type and requested type are the same there is nothing to do.
        if ((destType == null) || (destType == type)) return bytes;

        // If the binary data represents a YUV image that must be converted to JPEG data, an
        // intermediate YuvImage instance is created and yuvToJpeg is called. This avoids the much
        // slower conversion through an intermediate Bitmap for live YUY2 camera frames.
        if ((type == Type.YUV_NV21) || (type == Type.YUV_YUY2))
        {
            try
            {
                Integer yuvType = type.toImageFormat();
                if ((yuvType != null) && (destType == Type.JPEG))
                {
                    int quality = (option instanceof Integer)
                        ? Math.max(MIN_JPEG_QUALITY, Math.min(MAX_JPEG_QUALITY, ((Integer)option)))
                        : DEFAULT_JPEG_QUALITY;
                    return yuvToJpeg(new YuvImage(bytes, yuvType, width, height, null), quality);
                }
            }
            catch (Exception e)
            {
                return nullBytes("YUV conversion error - " + e.getMessage());
            }
        }

        // Convert the image using an intermediate bitmap.
        Bitmap bitmap = binaryToBitmap(bytes, type, width, height);
        if (bitmap != null) return bitmapToBinary(bitmap, destType, option);

        // In all other conversion has failed.
        return null;
    }

    /**
     * Compresses the current @e YUV image to @e JPEG image.
     *
     * @param   yuvImage        YuvImage instance containing YUV image data
     * @param   quality         JPEG compression quality
     *
     * @return  byte array containing JPEG image data
     */
    @Nullable
    private byte[] yuvToJpeg(YuvImage yuvImage, int quality)
    {
        // Return null if YUV image is not available.
        if (yuvImage == null) return nullBytes("YUV image object is not valid");

        // Compress YUV image to requested format.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        boolean result = yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), quality, stream);
        byte[] bytes = (result) ? stream.toByteArray() : null;
        if (bytes == null) return nullBytes("failed to convert YUV image object to JPEG image data");

        return bytes;
    }

    /**
     * Resizes the specified bitmap.
     *
     * A new bitmap is returned, the original bitmap is left untouched.
     *
     * @param   bitmap          Java @c Bitmap instance containing bitmap data
     * @param   newWidth        new width of image
     * @param   newHeight       new height of image
     *
     * @return  Java @c Bitmap instance containing resized image data, or @c null on failure
     */
    @Nullable
    private Bitmap resizeBitmap(Bitmap bitmap, int newWidth, int newHeight)
    {
        // Return null if bitmap data is not available.
        if (bitmap == null) return nullBitmap("bitmap image data is not available");

        // Return original bitmap data if both width and height are 0.
        if ((newWidth == 0) && (newHeight == 0)) return nullBitmap("width and height can not both be zero");

        Matrix matrix = new Matrix();
        float scaleX = (newWidth != 0) ? 1.0f*newWidth/bitmap.getWidth() : 1.0f*newHeight/bitmap.getHeight();
        float scaleY = (newHeight != 0) ? 1.0f*newHeight/bitmap.getHeight() : 1.0f*newWidth/bitmap.getWidth();
        matrix.postScale(scaleX, scaleY);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * Resizes the current @e YUV-NV21 image directly in raw form.
     *
     * @param   bytes           byte array containing image data
     * @param   width           current image width
     * @param   height          current image height
     * @param   newWidth        requested image width
     * @param   newHeight       requested image height
     *
     * @return  byte array containing resized image data, or @c null if resizing fails
     */
    @Nullable
    private byte[] resizeNv21Image(byte[] bytes, int width, int height, int newWidth, int newHeight)
    {
        // Return null if binary data is not available.
        if ((bytes == null) || (bytes.length == 0)) return nullBytes("YUV image data is not available");

        // Return null if both new width and height are 0.
        if ((newWidth == 0) && (newHeight == 0)) return nullBytes("width and height can not both be zero");

        // Compute and validate new image dimensions.
        if ((newWidth == 0) && (newHeight > 0)) newWidth = Math.round(newHeight*width/(float)height);
        else if ((newHeight == 0) && (newWidth > 0)) newHeight = Math.round(newWidth*height/(float)width);
        newWidth = normalizeDimension(newWidth, width);
        newHeight = normalizeDimension(newHeight, height);

        // Resize image.
        int yPlaneSize = newWidth*newHeight;
        byte[] newBytes = new byte[yPlaneSize+yPlaneSize/2];
        for (int y = 0; y<newHeight; y++)
        {
            int srcY = Math.min(height-1, (y*height)/newHeight);
            int srcRow = srcY *width;
            int dstRow = y*newWidth;
            for (int x = 0; x<newWidth; x++)
            {
                int srcX = Math.min(width- 1, (x*width)/newWidth);
                newBytes[dstRow+x] = bytes[srcRow+srcX];
            }
        }

        int srcUvOffset = width*height;
        int dstUvOffset = yPlaneSize;
        int srcHalfHeight = height/2;
        int dstHalfHeight = newHeight/2;
        for (int y=0; y<dstHalfHeight; y++)
        {
            int srcY = Math.min(srcHalfHeight - 1, (y*srcHalfHeight)/dstHalfHeight);
            int srcRow = srcUvOffset + srcY*width;
            int dstRow = dstUvOffset + y*newWidth;
            for (int x = 0; x<newWidth; x += 2)
            {
                int srcX = Math.min(width- 2, ((x*width)/newWidth) & ~1);
                newBytes[dstRow+x] = bytes[srcRow+srcX];
                newBytes[dstRow+x+1] = bytes[srcRow+srcX+1];
            }
        }
        if (newBytes.length == 0) return nullBytes("failed to resize YUV image");

        return newBytes;
    }

    /**
     * Returns a positive even image dimension.
     *
     * YUV image dimensions must be even.
     *
     * @param   requested       requested image width/height
     * @param   fallback        fallback image width/height
     *
     * The @p fallback value is returned if the specified dimension is odd or negative.
     *
     * @return  normalized image width/height
     */
    private int normalizeDimension(int requested, int fallback)
    {
        int value = (requested > 0) ? requested : fallback;
        if (value < 2) value = 2;
        if ((value & 1) != 0) value--;
        return Math.max(2, value);
    }

    /**
     * Mirrors the image horizontally or vertically.
     *
     * If the image is an @e RGB image it is mirrored using a dedicated method. For other image
     * types the bitmap data is mirrored if bitmap data is available. If bitmap data is not
     * available an intermediate bitmap is created and mirrored. The mirrored bitmap is converted
     * back to the current image type if nesessary (i.e. if current type is not bitmap). Mirroring
     * is only supported for the following image types:
     * - @e BITMAP
     * - @e RGB888
     * - @e RGB565
     * - @e PNG
     * - @e JPEG
     * - @e WEBP
     *
     * @param   hMirror         @c true (@c false) to mirror image horizontally (vertically)
     *
     * @return  this Image instance on success, @c null on failure
     */
    @Nullable
    private Image mirror(boolean hMirror)
    {
        // Return null if image data is not available.
        if (hasImage() == false) return nullImage("image data is not available");

        Bitmap newBitmap = null;
        byte[] newBytes = null;
        
        if (type == Type.RGB888)
        {
            // Use dedicated method fur reseizing RGB888 image.
            newBytes = (hMirror) ? mirrorHorizontalRgb(bytes, width, height) : mirrorVerticalRgb(bytes, width, height);
            if (newBytes == null) return nullImage("failed to mirror RGB image");
        }
        else
        {
            Matrix matrix = new Matrix();
            if (hMirror) matrix.preScale(-1.0f, 1.0f);
            else matrix.preScale(1.0f, -1.0f);
            
            if ((bitmap != null) && (origType == Type.BITMAP))
            {
                // Mirror bitmap image.
                newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (newBitmap == null) return nullImage("failed to mirror bitmap image");
            }
            else
            {
                // Return null if mirroring is not supported for image type. 
                if ((type != Type.PNG) && (type != Type.JPEG) && (type != Type.WEBP))
                    return nullImage("mirroring " + type.name() + " image is not supported");

                // Mirror stored or intermediate bitmap.
                Bitmap origBitmap = (bitmap != null) ? bitmap : binaryToBitmap(bytes, type, width, height);
                if (origBitmap == null) return null;
                newBitmap = Bitmap.createBitmap(origBitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (newBitmap == null) return nullImage("failed to mirror " + type.name() + " image");

                // Convert image back to current type.
                newBytes = bitmapToBinary(newBitmap, type, 100);
                if (newBytes == null) return null;
            }
        }
        
        // Store or clear bitmap data and binary data.
        storeBitmapData(newBitmap);
        storeBinaryData(newBytes, type, width, height);
        
        return this;
    }

    /**
     * Horizontally mirrors RGB image.
     *
     * @param   bytes           byte array containing binary image data to mirror
     * @param   width           width of image to mirror
     * @param   height          height of image to mirror
     *
     * @return  byte array containing mirrored image data, or @c null if mirroring fails
     */
    @Nullable
    private byte[] mirrorHorizontalRgb(byte[] bytes, int width, int height)
    {
        // Return null if image data is not available.
        if ((bytes == null) || (bytes.length == 0)) return nullBytes("binary data is not valid RGB data");

        byte[] mirrored = new byte[bytes.length];
        int rowSize = width*3;
        for (int y=0; y<height; y++)
        {
            int rowStart = y*rowSize;
            for (int x=0; x<width; x++)
            {
                int srcIndex = rowStart + x*3;
                int dstIndex = rowStart + (width - 1 - x)*3;
                mirrored[dstIndex] = bytes[srcIndex];
                mirrored[dstIndex+1] = bytes[srcIndex+1];
                mirrored[dstIndex+2] = bytes[srcIndex+2];
            }
        }
        return mirrored;
    }

    /**
     * Vertically mirrors @e RGB image.
     *
     * @param   bytes           byte array containing binary image data to mirror
     * @param   width           width of image to mirror
     * @param   height          height of image to mirror
     *
     * @return  byte array containing mirrored image data, or @c null if mirroring fails
     */
    @Nullable
    private byte[] mirrorVerticalRgb(byte[] bytes, int width, int height)
    {
        // Return null if image data is not available.
        if ((bytes == null) || (bytes.length == 0)) return nullBytes("binary data is not valid RGB data");


        byte[] mirrored = new byte[bytes.length];
        int rowSize = width*3;
        for (int y=0; y<height; y++)
        {
            int srcRow = y*rowSize;
            int dstRow = (height - 1 - y)*rowSize;
            System.arraycopy(bytes, srcRow, mirrored, dstRow, rowSize);
        }
        return mirrored;
    }

    /**
     * Draw frames in the bitmap image.
     *
     * @param   bitmap          @c Bitmap instance containing bitmap data
     * @param   lineColor       line color
     * @param   lineWidth       line width
     *
     * @return  @c Bitmap instance containing updated bitmap image data
     */
    private Bitmap drawFrames(@NonNull Bitmap bitmap, int lineColor, int lineWidth)
    {
        // Draw frame(s).
        Bitmap newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(lineColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(lineWidth);
        for (Rect frame : frames)
            canvas.drawRect(frame.left, frame.top, frame.right, frame.bottom, paint);

        // Empty frame list.
        frames.clear();

        return newBitmap;
    }

    /**
     * Draw text items in the bitmap image.
     *
     * @param   bitmap          @c Bitmap instance containing bitmap data
     *
     * @return  @c Bitmap instance containing updated bitmap image data
     */
    private Bitmap drawText(@NonNull Bitmap bitmap)
    {
        // Draw text item(s).
        Bitmap newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (TextItem text : textItems)
        {
            if (StringUtils.isBlank(text.text)) continue;

            Typeface typeface = Typeface.create(text.fontName, text.fontWeight);
            paint.setTextSize(text.fontSize);
            paint.setColor(text.fontColor);
            paint.setTypeface(typeface);
            canvas.drawText(text.text, text.left, text.bottom, paint);
        }

        // Empty frame list.
        textItems.clear();

        return newBitmap;
    }

    /**
     * Stores the bitmap data in member variable.
     *
     * @param   bitmap          instance of Java @c Bitmap class containing bitmap data to store
     * 
     * If @p bitmap is @c null the bitmap data is cleared.
     */
    private void storeBitmapData(Bitmap bitmap)
    {
        if (bitmap == null) this.bitmap = null;
        else
        {
            this.bitmap = bitmap;
            this.width = bitmap.getWidth();
            this.height = bitmap.getHeight();
        }
    }

    /**
     * Stores the binary data in member variables.
     *
     * @param   bytes           byte array containing binary image data to store
     * @param   type            type of image to store
     * @param   width           width of image to store
     * @param   height          height of image to store
     * 
     * If @p bytes is @c null an empty array, @c type is @c null, or the image size is 0 the binary 
     * data is cleared.
     */
    private void storeBinaryData(byte[] bytes, Type type, int width, int height)
    {
        if ((bytes == null) || (bytes.length == 0) || (type == Type.NONE) || (width == 0) || (height == 0))
        {
            this.bytes = null;
            this.type = Type.NONE;
        }
        else
        {
            this.bytes = bytes;
            this.type = type;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Determines the image size from the binary image data.
     *
     * The method matching the specified image type is called.
     *
     * @param   type            image type
     * @param   bytes           binary image data
     *
     * @return  Java @c Rect instance specifying image size, or @c null on failure
     */
    @Nullable
    private Rect getSize(@NonNull Type type, byte[] bytes)
    {
        switch (type)
        {
            case JPEG:
                return getJpegSize(bytes);
            case PNG:
                return getPngSize(bytes);
            default:
                return null;
        }
    }

    /**
     * Returns the @e JPEG image width and height.
     *
     * Note the <tt>& 0xFF</tt> when comparing bytes ... Java bytes are signed, so comparing to
     * values greater than 128 would always fail.
     *
     * @param   bytes           byte array containing JPEG image data
     *
     * @return  Java @c Rect instance specifying image size, or @c null on failure
     */
    @Nullable
    private Rect getJpegSize(@NonNull byte[] bytes)
    {
        int pos = 0;
        for (; pos<bytes.length-9; pos++)
        {
            if ((bytes[pos] & 0xFF) == 0xFF)
            {
                int b = bytes[pos+1] & 0xFF;
                if ((b >= 0xC0) && (b <= 0xCF) && (b != 0xC4) && (b != 0xC8) && (b != 0xCC))
                {
                    int height = ((bytes[pos+5] & 0xFF) << 8) | (bytes[pos+6] & 0xFF);
                    int width = ((bytes[pos+7] & 0xFF) << 8) | (bytes[pos+8] & 0xFF);
                    return new Rect(0, 0, width, height);
                }
            }
        }
        return null;
    }

    /**
     * Returns the @e JPEG image width and height.
     *
     * Note the <tt>& 0xFF</tt> when comparing bytes ... Java bytes are signed, so comparing to
     * values greater than 128 would always fail.
     *
     * @param   bytes           byte array containing PNG image data
     *
     * @return  Java @c Rect instance specifying image size, or @c null on failure
     */
    @Nullable
    private Rect getPngSize(@NonNull byte[] bytes)
    {
        if (bytes.length >= 24)
        {
            int width = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16) | ((bytes[18] & 0xFF) << 8)  | (bytes[19] & 0xFF);
            int height = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16) | ((bytes[22] & 0xFF) << 8)  | (bytes[23] & 0xFF);

            return new Rect(0, 0, width, height);
        }
        return null;
    }

    /**
     * Retrieves pixels from specified bitmap.
     *
     * Each pixel is represented by a single integer specifying the full RGB pixel color.
     *
     * @param   bitmap          bitmap from which to retrieve pixel
     *
     * @return  integer array containing pixel data
     */
    @Nullable
    private int[] getPixelsFromBitmap(Bitmap bitmap)
    {
        // Return null if bitmap is not available.
        if (bitmap == null)
        {
            lastError = ("bitmap image data is not valid");
            return null;
        }

        // Get pixels from bitmap.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width*height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return pixels;
    }

    /**
     * Converts the specified integer value to a byte value.
     *
     * If the specified value is smaller than 0 the value is set to 0, if larger than 255 it is
     * set to 255.
     *
     * @param   value           integer vlue to be converted
     *
     * @return  integer value converted to byte
     */
    private byte toByte(int value)
    {
        if (value < 0) return 0;
        if (value > 255) return (byte)255;
        return (byte)(value & 0xFF);
    }

    /**
     * Sets error message and returns an Image instance with a @c null value.
     *
     * @param   error           error message
     *
     * @return  @c null as Image instance
     */
    @Nullable
    private Image nullImage(String error)
    {
        this.lastError = error;
        return null;
    }

    /**
     * Sets error message and returns a @c Bitmap instance with a @c null value.
     *
     * @param   error           error message
     *
     * @return  @c null as @c Bitmap instance
     */
    @Nullable
    private Bitmap nullBitmap(String error)
    {
        this.lastError = error;
        return null;
    }

    /**
     * Sets error message and returns a byte array with a @c null.
     *
     * @param   error           error message
     *
     * @return  @c null as byte array
     */
    @Nullable
    private byte[] nullBytes(String error)
    {
        this.lastError = error;
        return null;
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Specifies supported image types.
     *
     * The enumerator provides a mapping on both Java @c ImageFormat and @c Bitmap.CompressFormat
     * enumerators.
     */
    public enum Type {

        /** Specifies the image type is not set. */
        NONE(null, null, null),
        BITMAP(null, null, null),
        RGB888(null, ImageFormat.FLEX_RGB_888, null),
        RGB565(null, ImageFormat.RGB_565, null),
        GRAY8(null, null, null),
        PNG(Bitmap.CompressFormat.PNG, null, "png"),
        JPEG(Bitmap.CompressFormat.JPEG, ImageFormat.JPEG, "jpg"),
        WEBP(Bitmap.CompressFormat.WEBP, null, "webp"),
        YUV_NV21(null, ImageFormat.NV21, null),
        YUV_YUY2(null, ImageFormat.YUY2, null),
        RAW(null, null, "bin");

        /** Bitmap compression format. */
        private final Bitmap.CompressFormat compressFormat;

        /** YUV image encoding type. */
        private final Integer imageFormat;

        /** File extension. */
        private final String fileExtension;

        /**
         * Creates enumerator value matching the bitmap compression format and integer image format.
         *
         * @param   compressFormat  bitmap compression format
         * @param   imageFormat     integer image format
         * @param   fileExtension   file extension
         */
        Type(Bitmap.CompressFormat compressFormat, Integer imageFormat, String fileExtension)
        {
            this.compressFormat = compressFormat;
            this.imageFormat = imageFormat;
            this.fileExtension = fileExtension;
        }

        /**
         * Returns bitmap compression format matching enumerator value.
         *
         * @return  bitmap compression format matching enumerator value
         *
         * Not all image type have a matching bitmap compression format. If no matching compression
         * format is set for the image type this method returns @c null.
         */
        @Nullable
        public Bitmap.CompressFormat toCompressFormat()
        {
            return compressFormat;
        }

        /**
         * Returns the integer image format matching the enumerator value.
         *
         * @return  integer image format matching enumerator value
         *
         * Not all image type have a matching integer image image format. If no matching integer
         * image format is set for the image type this method returns @c null.
         */
        @Nullable
        public Integer toImageFormat()
        {
            return imageFormat;
        }

        /**
         * Returns the file extension matching the enumerator value.
         *
         * @return  file extension matching enumerator value
         *
         * File extensions are only specified for image types that can be saved to file.
         */
        public String toFileExtension() { return fileExtension; }

        /**
         * Returns the enumerator value matching the specified name.
         *
         * @param   name        name of enumerator value to return
         *
         * @return  Type enumerator value with specified name name
         *
         * If there is no enumerator value matching the specified name the method returns @c null.
         */
        public static Type fromName(String name)
        {
            if (name == null) return null;

            name = name.trim();
            for (Type type : values())
            {
                if (name.equalsIgnoreCase(type.name())) return type;
            }
            return null;
        }

        /**
         * Returns the enumerator value matching the specified bitmap compress format.
         *
         * @param   compressFormat  bitmap compress format
         *
         * @return  Type enumerator value matching bitmap compress format
         *
         * If there is no enumerator value matching the bitmap compress format the method returns
         * @c null.
         */
        public static Type fromCompressFormat(Bitmap.CompressFormat compressFormat)
        {
            if (compressFormat == null) return null;

            for (Type type : values())
            {
                if (type.compressFormat != null && type.compressFormat == compressFormat)  return type;
            }
            return null;
        }

        /**
         * Sets the enumerator value matching the specified integer image format.
         *
         * @param   imageFormat     integer image format
         *
         * @return  Type enumerator value matching integer image format
         *
         * If there is no enumerator value matching the integer image format the method returns the
         * NONE value.
         */
        public static Type fromImageFormat(int imageFormat)
        {
            for (Type type : values())
            {
                if (type.imageFormat != null && type.imageFormat == imageFormat)  return type;
            }
            return NONE;
        }
    }

    /** Stores details on text item to be drawn in image. */
    static class TextItem
    {
        /** Text to be drawn. */
        private final String text;

        /*** Left position of text, negative value means position relative to right of image. */
        private final float left;

        /*** Bottom position of text, negative value means position relative to bottom of image. */
        private final float bottom;

        /** Font name. */
        private final String fontName;

        /** Font weight. */
        private final int fontWeight;

        /** Font size */
        private final float fontSize;

        /** Font color. */
        private final int fontColor;

        /**
         * Constructs a new text item.
         *
         * All parameters are copied to member variables.
         *
         * @param   text            text to be added to bitmap
         * @param   left            left position of text
         * @param   bottom          top position of text
         * @param   fontName        font name
         * @param   fontWeight      font weight
         * @param   fontSize        font size
         * @param   fontColor       font color
         */
        private TextItem(String text, float left, float bottom, String fontName, int fontWeight, float fontSize, int fontColor)
        {
            this.text = text;
            this.left = left;
            this.bottom = bottom;
            this.fontName = fontName;
            this.fontWeight = fontWeight;
            this.fontSize = fontSize;
            this.fontColor = fontColor;
        }
    }
}
