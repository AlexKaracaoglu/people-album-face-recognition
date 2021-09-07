import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.S3Event
import mu.KotlinLogging
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.rekognition.RekognitionClient
import software.amazon.awssdk.services.rekognition.model.Image
import software.amazon.awssdk.services.rekognition.model.S3Object
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.Tag
import software.amazon.awssdk.services.s3.model.Tagging
import java.awt.BasicStroke
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


private val logger = KotlinLogging.logger {  }

class DetectAndCropFaces(
    private val rekognitionClient: RekognitionClient = RekognitionClient.create(),
    private val s3Client: S3Client = S3Client.create()
): RequestHandler<S3Event, Unit> {

    override fun handleRequest(s3Event: S3Event, context: Context): Unit {
        val bucketName = s3Event.records[0].s3.bucket.name
        val key = s3Event.records[0].s3.`object`.key

        val s3Object = S3Object.builder().bucket(bucketName).name(key).build()
        logger.info { "S3Object: $s3Object" }

        val image = Image.builder().s3Object(s3Object).build()

        val faceDetection = rekognitionClient.detectFaces { it.image(image) }
        logger.info { "Faces detected: ${faceDetection.faceDetails().size}" }

        val bufferedImage = ImageIO.read(s3Client.getObject { it.bucket(bucketName).key(key) })
        val imageWidth = bufferedImage.width
        val imageHeight = bufferedImage.height
        logger.info { "Image width: $imageWidth, height: $imageHeight" }

        val fullImage = bufferedImage.createGraphics().apply {
            stroke = BasicStroke(3f)
        }

        faceDetection.faceDetails().forEach {
            val detailBoundaryBox = it.boundingBox()
            val crop = cropImage(bufferedImage, detailBoundaryBox.top() * imageHeight, detailBoundaryBox.left() * imageWidth, detailBoundaryBox.width() * imageWidth, detailBoundaryBox.height() * imageHeight)
            if (crop != null) {
                uploadBufferedImageToS3(crop, it.hashCode().toString(), "crop/cropped-faces/${key.split("/").last().removeSuffix(".png")}", key, bucketName)
                fullImage.drawRect((detailBoundaryBox.left() * imageWidth).toInt(), (detailBoundaryBox.top() * imageHeight).toInt(), (detailBoundaryBox.width() * imageWidth).toInt(), (detailBoundaryBox.height() * imageHeight).toInt())
            }

        }
        uploadBufferedImageToS3(bufferedImage, "identifiedFaces", "crop/identified-faces/${key.split("/").last().removeSuffix(".png")}", key, bucketName)
    }

    private fun cropImage(sourceImage: BufferedImage, top: Float, left: Float, width: Float, height: Float): BufferedImage? {
        return sourceImage.getSubimage(left.toInt(), top.toInt(), width.toInt(), height.toInt())
    }

    private fun uploadBufferedImageToS3(sourceImage: BufferedImage, fileName: String, path: String, originalImageKey: String, bucketName: String) {
        val outstream = ByteArrayOutputStream()
        ImageIO.write(sourceImage, "png", outstream)
        val buffer = outstream.toByteArray()
        val inputStream = ByteArrayInputStream(buffer)
        val meta = mapOf(
            "Content-Type" to "image/png"
        )
        val request = PutObjectRequest.builder().bucket(bucketName).contentType("image/png").contentLength(buffer.size.toLong()).key("$path/$fileName.png").metadata(meta).tagging(Tagging.builder().tagSet(Tag.builder().key("originalImageKey").value(originalImageKey).build()).build()).build()
        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, buffer.size.toLong()))
    }

}