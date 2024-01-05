package cn.lili.controller.common;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.lili.cache.Cache;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.security.AuthUser;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.security.enums.UserEnums;
import cn.lili.common.utils.Base64DecodeMultipartFile;
import cn.lili.common.utils.CommonUtil;
import cn.lili.common.vo.ResultMessage;

import cn.lili.modules.file.plugin.FilePluginFactory;
import cn.lili.modules.file.service.FileService;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件上传接口
 *
 * @author Chopper
 * @since 2020/11/26 15:41
 */
@Slf4j
@RestController
@Api(tags = "文件上传接口")
@RequestMapping("/common/common/upload")
public class UploadController {

    @Autowired
    private FileService fileService;
    @Autowired
    private SettingService settingService;
    @Autowired
    private FilePluginFactory filePluginFactory;
    @Autowired
    private Cache cache;

    @ApiOperation(value = "文件上传")
    @PostMapping(value = "/file")
    public ResultMessage<Object> upload(MultipartFile file,
                                        String base64,
                                        @RequestHeader String accessToken, @RequestParam String directoryPath) {


        AuthUser authUser = UserContext.getAuthUser(cache, accessToken);
        //如果用户未登录，则无法上传图片
        if (authUser == null) {
            throw new ServiceException(ResultCode.USER_AUTHORITY_ERROR);
        }
        if (file == null) {
            throw new ServiceException(ResultCode.FILE_NOT_EXIST_ERROR);
        }
        Setting setting = settingService.get(SettingEnum.OSS_SETTING.name());
        if (setting == null || CharSequenceUtil.isBlank(setting.getSettingValue())) {
            throw new ServiceException(ResultCode.OSS_NOT_EXIST);
        }
        if (CharSequenceUtil.isEmpty(file.getContentType())) {
            throw new ServiceException(ResultCode.IMAGE_FILE_EXT_ERROR);
        }


        if (!CharSequenceUtil.containsAny(Objects.requireNonNull(file.getContentType()).toLowerCase(), "image", "video")) {
            throw new ServiceException(ResultCode.FILE_TYPE_NOT_SUPPORT);
        }

        if (CharSequenceUtil.isNotBlank(base64)) {
            //base64上传
            file = Base64DecodeMultipartFile.base64Convert(base64);
        }
        String result;
        String fileKey = CommonUtil.rename(Objects.requireNonNull(file.getOriginalFilename()));
        cn.lili.modules.file.entity.File newFile = new cn.lili.modules.file.entity.File();
        try {
            InputStream inputStream = file.getInputStream();
            //上传至第三方云服务或服务器
            String scene = UserContext.getCurrentUser().getRole().name();
            if (StrUtil.equalsAny(UserContext.getCurrentUser().getRole().name(), UserEnums.MEMBER.name(), UserEnums.STORE.name(), UserEnums.SEAT.name())) {
                scene = scene + "/" + authUser.getId();
            }
            fileKey = scene + "/" + directoryPath + "/" + fileKey;
            //上传至第三方云服务或服务器
            result = filePluginFactory.filePlugin().inputStreamUpload(inputStream, fileKey);
            //保存数据信息至数据库
            newFile.setName(file.getOriginalFilename());
            newFile.setFileSize(file.getSize());
            newFile.setFileType(file.getContentType());
            newFile.setFileKey(fileKey);
            newFile.setUrl(result);
            newFile.setCreateBy(authUser.getUsername());
            newFile.setUserEnums(authUser.getRole().name());
            //如果是店铺，则记录店铺id
            if (authUser.getRole().equals(UserEnums.STORE)) {
                newFile.setOwnerId(authUser.getStoreId());
            } else {
                newFile.setOwnerId(authUser.getId());
            }

            //存储文件目录
            if (StrUtil.isNotEmpty(directoryPath)) {
                if (directoryPath.indexOf("/") > 0) {
                    newFile.setFileDirectoryId(directoryPath.substring(directoryPath.lastIndexOf("/") + 1));
                } else {
                    newFile.setFileDirectoryId(directoryPath);
                }
            }
            fileService.save(newFile);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new ServiceException(ResultCode.OSS_EXCEPTION_ERROR);
        }
        return ResultUtil.data(result);
    }




    @Value("D:\\user\\11")
    private String uploadPath;


    @ApiOperation(value = "用户未登录文件上传")
    @PostMapping(value = "/fileBadge")
    public ResultMessage<Object> uploadBadge(MultipartFile file,
                                        String base64,
                                        @RequestParam String directoryPath) throws IOException {


        //获取 原始文件名
        String originalFileName = file.getOriginalFilename();
        System.out.println("原始文件名：" + originalFileName);

        //断言 判断文件名是否有值 没有则抛出异常中断程序执行
        assert originalFileName != null;

        //使用UUID通用唯一识别码 + 后缀名的形式
        //设置唯一文件路径 防止文件名重复 出现覆盖的情况
        String fileName = UUID.randomUUID().toString() + originalFileName.substring(originalFileName.lastIndexOf("."));
        //打印查看
        System.out.println("唯一文件名：" + fileName);

        // 指定文件保存的路径
        String filePath = uploadPath + fileName;

        //文件名保存到对应数据的头像图片字段
        cn.lili.modules.file.entity.File emp = new cn.lili.modules.file.entity.File();
        //将文件名保存到数据库表的头像字段
        emp.setName(file.getOriginalFilename());
        emp.setFileSize(file.getSize());
        emp.setFileType(file.getContentType());
        emp.setFileKey(filePath);
        emp.setUrl(filePath);
        fileService.save(emp);

        //根据上传路径创建文件夹File对象
        File saveAddress = new File(uploadPath);
        if (!saveAddress.exists()) {
            saveAddress.mkdirs();// 如果文件夹不存在 创建保存文件对应的文件夹
        }
        // 将上传的文件保存到指定路径
        file.transferTo(new File(filePath));

        return ResultUtil.data(filePath);

    }

}
