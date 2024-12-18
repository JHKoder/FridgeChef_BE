package Fridge_Chef.team.image.service;

import Fridge_Chef.team.config.model.ImageConfigMeta;
import Fridge_Chef.team.exception.ApiException;
import Fridge_Chef.team.exception.ErrorCode;
import Fridge_Chef.team.image.domain.Image;
import Fridge_Chef.team.image.domain.ImageType;
import Fridge_Chef.team.image.repository.ImageRepository;
import Fridge_Chef.team.user.domain.User;
import Fridge_Chef.team.user.domain.UserId;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile({"prod", "dev"})
public class ImageProdService implements ImageService {
    private final static Pattern mimePattern = Pattern.compile("image/(png|jpeg|jpg)");
    private final UploadManager uploadManager;
    private final ObjectStorage objectStorageClient;
    private final ImageRepository imageRepository;
    private final ImageConfigMeta imageConfigMeta;

    public ImageProdService(UploadManager uploadManager, ObjectStorage objectStorageClient, ImageRepository imageRepository, ImageConfigMeta imageConfigMeta) {
        this.uploadManager = uploadManager;
        this.objectStorageClient = objectStorageClient;
        this.imageRepository = imageRepository;
        this.imageConfigMeta = imageConfigMeta;
    }

    @Override
    @Transactional
    public Image uploadImageWithId(UserId userId, boolean isImage, Long imageId, MultipartFile file) {
        if (isImage) {
            return imageUpload(userId, file);
        }
        return imageRepository.findById(imageId)
                .orElse(imageUpload(userId, file));
    }

    @Transactional
    public Image imageUpload(UserId userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.info("image null");
            return imageRepository.save(Image.none());
        }
        String fileName = onlyNameChange(file.getOriginalFilename());
        upload(file, fileName);
        Image image = new Image(imageConfigMeta.getUrl(), imageConfigMeta.getUploadPath(), fileName, ImageType.ORACLE_CLOUD, userId);
        log.info("size : "+file.getSize() +" , isEmpty() : "+file.isEmpty() + ","+file.getContentType());
        log.info("[Image] upload success " + fileName);
        return imageRepository.save(image);
    }

    @Transactional
    public Image imageUploadUserPicture(User user, MultipartFile file) {
        if (file.isEmpty()) {
            return imageRepository.save(Image.none());
        }
        String fileName = onlyNameChange(file.getOriginalFilename());
        upload(file, fileName);
        Image image = new Image(imageConfigMeta.getUrl(), imageConfigMeta.getUploadPath(), fileName, ImageType.ORACLE_CLOUD, user.getUserId());
        log.info("image upload success User : " + user.getUserId() + " _ , " + fileName);
        imageRepository.save(image);
        user.updatePicture(image);
        return image;
    }

    @Transactional
    public Image openApiUriImageSave(String path) {
        return imageRepository.save(new Image(path, ImageType.OUT_URI));
    }

    @Transactional
    public void imageRemove(UserId userId, Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ApiException(ErrorCode.IMAGE_NOT_ID));

        if (image.getType() == null || image.getType().equals(ImageType.OUT_URI)) {
            return;
        }

        if (!image.getUserId().equals(userId.getValue())) {
            throw new ApiException(ErrorCode.IMAGE_AUTHOR_MISMATCH);
        }

        FileRemoveManager manager = new FileRemoveManager(objectStorageClient, imageConfigMeta, image);
        manager.remove();
        imageRepository.delete(image);
        log.info("image remove success User : " + userId + " _ , " + image.getName());
    }

    @Transactional
    public void imageRemove(Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ApiException(ErrorCode.IMAGE_NOT_ID));
        imageRemove(image);
    }

    @Override
    public void imageRemove(Image image) {
        FileRemoveManager manager = new FileRemoveManager(objectStorageClient, imageConfigMeta, image);
        manager.remove();
        log.info("image.id remove success : " + image.getName());
        imageRepository.delete(image);
    }

    public void upload(MultipartFile multipartFile, String fileName) {
        FileUploadManager file = new FileUploadManager(uploadManager, imageConfigMeta, multipartFile, fileName);
        file.upload();
        log.info(fileName+"image update");
    }

    public void filter(MultipartFile file) {
        String type = file.getContentType();
        if (type == null || !mimePattern.matcher(type).matches()) {
            throw new ApiException(ErrorCode.IMAGE_CONTENT_TYPE_FAIL);
        }
    }

    public void filters(List<MultipartFile> files) {
        files.forEach(this::filter);
    }

    public List<Image> imageUploads(UserId userId, List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        filters(files);
        return files.stream()
                .map(file -> imageUpload(userId, file))
                .collect(Collectors.toList());
    }


    private String onlyNameChange(String name) {
        String cleanedName = name.replaceAll("\\s", "");
        String uuidString = UUID.randomUUID().toString();
        return uuidString + "_" + cleanedName;
    }
}
