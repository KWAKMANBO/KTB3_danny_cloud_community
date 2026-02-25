package com.ktb.community.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

@Service
public class FileProcessService {

    private final String fileDestination;

    public FileProcessService(@Value("{$FILE_STORAGE_PATH}") String fileDestination) {
        this.fileDestination = fileDestination;
    }

    public void compressImage(File imageFile) throws Exception {
        try {
            File compressedImageFile = new File(fileDestination + imageFile.getName());

            InputStream is = new FileInputStream(imageFile);
            OutputStream os = new FileOutputStream(compressedImageFile);

            float quality = 0.6f;

            BufferedImage image = ImageIO.read(is);
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");

            if (!writers.hasNext()) throw new IllegalStateException("No writers found");

            ImageWriter writer = (ImageWriter) writers.next();
            ImageOutputStream ios = ImageIO.createImageOutputStream(os);
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image,null,null), param);
            is.close();
            os.close();
            ios.close();
            writer.dispose();


        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
