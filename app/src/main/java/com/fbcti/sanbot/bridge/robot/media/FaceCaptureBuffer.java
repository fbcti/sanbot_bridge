/**
 * @file        FaceCaptureBuffer.java
 * @brief       Implements FaceCaptureBuffer class.
 */
package com.fbcti.sanbot.bridge.robot.media;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.util.MapUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buffers a the last 5 face captures received from the Sanbot face recognition feature.
 *
 * This class stores up to 5 face captures received from the SanbotCameraManager instance to
 * ensure the captures remains available until either explicitly discarded or replaced by a new
 * capture (see @ref FACE_CAPTURE "here"). The captures are stored as received, i.e. as bitmap
 * images and Java @c Maps instance containing associated face data. If getFaceCapture() is called
 * to retrieve a face capture, an Image instance is created from the bitmap and the face data is
 * added as metadata.
 *
 * @version     1.0.001
 * @date        19 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class FaceCaptureBuffer
{
    /** Maximum number of face captures in buffer. */
    private final static int CAPTURE_COUNT_MAX = 5;

    /** Face captures. */
    private final List<FaceCapture> faceCaptures = new ArrayList<>();

    /** Number of face detection events received. */
    private long numReceived= 0L;

    /** Number of face captures retrieved. */
    private long numRetrieved= 0L;

    /** Flag specifying if current capture has already been retrieved. */
    private boolean isRetrieved = false;

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Copies the face image and face detection data to the list of face capture items.
     *
     * Copies of the face image and face detection data are stored, not the original image and data.
     *
     * @param   image           face image as bitmap
     * @param   data            face detection data
     */
    public void setData(Bitmap image, @NonNull Map<String, Object> data)
    {
        Bitmap copyBitmap = Bitmap.createBitmap(image);
        Map<String, Object> copyData = MapUtils.deepCopy(data);
        synchronized (this)
        {
            if (faceCaptures.size() >= CAPTURE_COUNT_MAX) faceCaptures.remove(0);
            faceCaptures.add(new FaceCapture(copyBitmap, copyData));
            numReceived++;
            isRetrieved = false;
        }
    }

    /**
     * Returns buffered capture if available.
     *
     * The face capture at the specified index is returned. LIFO order is used for retrieving items,
     * so index 0 returns the last item in the list, index 1 returns the one but last item, etc. A
     * new Image instance is created from the stored bitmap, and the timestamp and meta data are
     * added to the image.
     *
     * @param   lifo        last in-first out index of face capture to retrieve
     *
     * @return  Image instance containing buffered capture, or @c null if capture is not available
     */
    @Nullable
    public synchronized Image getFaceCapture(int lifo)
    {
        int index = (lifo >= 0) ? faceCaptures.size() - lifo - 1 : -1;
        if (index < 0) return null;

        FaceCapture faceCapture = faceCaptures.get(index);
        if (faceCapture.bitmap == null) return null;

        Image image = new Image(faceCapture.bitmap);
        if (image.hasImage() == false) return null;

        if (isRetrieved == false) numRetrieved++;
        isRetrieved = true;
        image.setData(faceCapture.timestamp, faceCapture.data);
        return image;
    }

    /**
     * Builds a data map containing current buffer status data.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    public synchronized Map<String, Object> buildStatusData()
    {
        Map<String, Object> data  = new LinkedHashMap<>();
        data.put("faceCaptureCount", faceCaptures.size());
        data.put("facesReceived", numReceived);
        data.put("facesRetrieved", numRetrieved);
        return data;
    }

    /**
     * Helper class storing a single face capture.
     */
    private static class FaceCapture
    {
        /** Last received face image. */
        private final Bitmap bitmap;

        /** Last received face detection data. */
        private final Map<String, Object> data;

        /** Timestamp of last received face detection event. */
        private final long timestamp;

        /**
         * Constructs a new FaceCapture instance.
         *
         * The bitmap and dara are copied to member variables, ane the current timestamp is set.
         *
         * @param   bitmap          face capture bitmap
         * @param   data            face capture data
         */
        FaceCapture(Bitmap bitmap, Map<String, Object> data)
        {
            this.bitmap = bitmap;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
}