package com.peterica.swagger.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AWSService {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private final AmazonS3 s3Client;

    /**
     * aws s3 로 파일 업로드
     *
     * @param file
     * @return
     */
    public String uploadToAWS(MultipartFile file) {
        String key = UUID.randomUUID() + "_" + file.getOriginalFilename();
        try {

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            PutObjectRequest request = new PutObjectRequest(bucketName, key, file.getInputStream(), metadata);
            request.withCannedAcl(CannedAccessControlList.AuthenticatedRead); // 접근권한 체크
            PutObjectResult result = s3Client.putObject(request);
            return key;
        } catch (AmazonServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            log.error("uploadToAWS AmazonServiceException filePath={}, yyyymm={}, error={}", e.getMessage());
        } catch (SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            log.error("uploadToAWS SdkClientException filePath={}, error={}", e.getMessage());
        } catch (Exception e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            log.error("uploadToAWS SdkClientException filePath={}, error={}", e.getMessage());
        }

        return "";
    }

    public void delete(String fileKey) {
        s3Client.deleteObject(bucketName, fileKey);
    }

    public void rename(String sourceKey, String destinationKey){
        s3Client.copyObject(
                bucketName,
                sourceKey,
                bucketName,
                destinationKey
        );
        s3Client.deleteObject(bucketName, sourceKey);
    }

    /**
     * file 다운로드
     *
     * @param fileKey  파일 key 로 해당 버킷에서 파일 찾아서 들고옴
     * @param downloadFileName 다운로드 파일명
     * @param request
     * @param response
     * @return
     */
    public boolean download(String fileKey, String downloadFileName, HttpServletRequest request, HttpServletResponse response) {
        if (fileKey == null) {
            return false;
        }
        S3Object fullObject = null;
        try {
            fullObject = s3Client.getObject(bucketName, fileKey);
            if (fullObject == null) {
                return false;
            }
        } catch (AmazonS3Exception e) {
            throw new BadRequestException("다운로드 파일이 존재하지 않습니다.");
        }

        OutputStream os = null;
        FileInputStream fis = null;
        boolean success = false;
        try {
            S3ObjectInputStream objectInputStream = fullObject.getObjectContent();
            byte[] bytes = IOUtils.toByteArray(objectInputStream);

            String fileName = null;
            if(downloadFileName != null) {
                //fileName= URLEncoder.encode(downloadFileName, "UTF-8").replaceAll("\\+", "%20");
                fileName=  getEncodedFilename(request, downloadFileName);
            } else {
                fileName=  getEncodedFilename(request, fileKey); // URLEncoder.encode(fileKey, "UTF-8").replaceAll("\\+", "%20");
            }

            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Transfer-Encoding", "binary");
            response.setHeader( "Content-Disposition", "attachment; filename=\"" + fileName + "\";" );
            response.setHeader("Content-Length", String.valueOf(fullObject.getObjectMetadata().getContentLength()));
            response.setHeader("Set-Cookie", "fileDownload=true; path=/");
            FileCopyUtils.copy(bytes, response.getOutputStream());
            success = true;
        } catch (IOException e) {
            log.debug(e.getMessage(), e);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                log.debug(e.getMessage(), e);
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                log.debug(e.getMessage(), e);
            }
        }
        return success;
    }


    private String getEncodedFilename(HttpServletRequest request, String displayFileName) throws UnsupportedEncodingException {
        String header = request.getHeader("User-Agent");

        String encodedFilename = null;
        if (header.indexOf("MSIE") > -1) {
            encodedFilename = URLEncoder.encode(displayFileName, "UTF-8").replaceAll("\\+", "%20");
        } else if (header.indexOf("Trident") > -1) {
            encodedFilename = URLEncoder.encode(displayFileName, "UTF-8").replaceAll("\\+", "%20");
        } else if (header.indexOf("Chrome") > -1) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < displayFileName.length(); i++) {
                char c = displayFileName.charAt(i);
                if (c > '~') {
                    sb.append(URLEncoder.encode("" + c, "UTF-8"));
                } else {
                    sb.append(c);
                }
            }
            encodedFilename = sb.toString();
        } else if (header.indexOf("Opera") > -1) {
            encodedFilename = "\"" + new String(displayFileName.getBytes("UTF-8"), "8859_1") + "\"";
        } else if (header.indexOf("Safari") > -1) {
            encodedFilename = URLDecoder.decode("\"" + new String(displayFileName.getBytes("UTF-8"), "8859_1") + "\"", "UTF-8");
        } else {
            encodedFilename = URLDecoder.decode("\"" + new String(displayFileName.getBytes("UTF-8"), "8859_1") + "\"", "UTF-8");
        }
        return encodedFilename;

    }

}
