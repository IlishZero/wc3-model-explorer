package com.hiveworkshop.edit;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Animated GIF encoder on the plain JDK ImageIO GIF writer — no external
 * dependencies. Frames must share one image type (the viewer screenshots do).
 * The NETSCAPE2.0 loop extension is attached to the first frame's metadata,
 * which is how the standard plugin expects it.
 */
public final class GifSequenceWriter implements AutoCloseable {

    private static final String IMAGE_FORMAT = "javax_imageio_gif_image_1.0";

    private final ImageWriter writer;
    private final ImageWriteParam params;
    private final ImageTypeSpecifier specifier;
    private final boolean loopForever;
    private ImageOutputStream stream;
    private boolean firstFrame = true;

    public GifSequenceWriter(BufferedImage firstFrameImage, boolean loopForever, Path target) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) throw new IOException("No GIF writer available");
        writer = writers.next();
        params = writer.getDefaultWriteParam();
        specifier = ImageTypeSpecifier.createFromRenderedImage(firstFrameImage);
        this.loopForever = loopForever;
        IIOMetadata streamMetadata = writer.getDefaultStreamMetadata(params);
        stream = ImageIO.createImageOutputStream(target.toFile());
        writer.setOutput(stream);
        writer.prepareWriteSequence(streamMetadata);
    }

    /** Appends one frame; {@code delayMillis} is rounded to GIF's 1/100 s units. */
    public void writeFrame(BufferedImage image, int delayMillis) throws IOException {
        IIOMetadata metadata = writer.getDefaultImageMetadata(specifier, params);
        var root = toTree(metadata);

        var children = root.getChildNodes();
        for (int i = 0; children != null && i < children.getLength(); i++) {
            var node = children.item(i);
            if ("GraphicControlExtension".equals(node.getNodeName())) {
                node.getAttributes().getNamedItem("disposalMethod")
                        .setNodeValue("restoreToBackgroundColor");
                node.getAttributes().getNamedItem("delayTime")
                        .setNodeValue(String.valueOf(Math.max(2, Math.round(delayMillis / 10.0))));
            }
        }
        if (firstFrame) {
            attachNetscapeLoop(root, loopForever ? 0 : 1);
            firstFrame = false;
        }
        metadata.setFromTree(IMAGE_FORMAT, root);
        writer.writeToSequence(new IIOImage(image, null, metadata), params);
    }

    /** NETSCAPE2.0 application extension; loopCount 0 = forever. */
    private static void attachNetscapeLoop(org.w3c.dom.Node root, int loopCount) {
        byte[] netscape = new byte[]{
                0x21, (byte) 0xFF, 0x0B,
                'N', 'E', 'T', 'S', 'C', 'A', 'P', 'E', '2', '.', '0',
                0x03, 0x01,
                (byte) (loopCount & 0xFF), (byte) ((loopCount >> 8) & 0xFF),
                0x00
        };
        var extensions = new javax.imageio.metadata.IIOMetadataNode("ApplicationExtensions");
        var extension = new javax.imageio.metadata.IIOMetadataNode("ApplicationExtension");
        extension.setAttribute("applicationID", "NETSCAPE");
        extension.setAttribute("authenticationCode", "2.0");
        extension.setUserObject(netscape);
        extensions.appendChild(extension);
        root.appendChild(extensions);
    }

    private static org.w3c.dom.Node toTree(IIOMetadata metadata) throws IIOInvalidTreeException {
        return metadata.getAsTree(IMAGE_FORMAT);
    }

    @Override public void close() {
        try {
            writer.endWriteSequence();
        } catch (Exception ignored) {}
        try {
            writer.dispose();
        } catch (Exception ignored) {}
        try {
            if (stream != null) {
                stream.flush();
                stream.close();
            }
        } catch (Exception ignored) {}
    }
}
