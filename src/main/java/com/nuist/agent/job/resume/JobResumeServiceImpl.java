package com.nuist.agent.job.resume;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.entity.JobResumeVersion;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.common.PrivacyMasker;
import com.nuist.agent.job.config.JobProperties;
import com.nuist.agent.job.dto.JobDtos;
import com.nuist.agent.job.dto.ViewDtos;
import com.nuist.agent.job.rag.JobVectorService;
import com.nuist.agent.job.rag.JobVectorServiceImpl;
import com.nuist.agent.mapper.JobResumeMapper;
import com.nuist.agent.mapper.JobResumeVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 简历资料管理实现（成员 B）。
 *
 * <p>关键设计：
 * <ul>
 *   <li>所有操作都带 userId 条件，避免越权读写他人简历；</li>
 *   <li>正文入库前统一由 {@link PrivacyMasker} 脱敏（手机号 / 邮箱 / 身份证 / 银行卡）；</li>
 *   <li>正文变化时自动留存历史版本，支持手动存版本与回滚，历史只增不改。</li>
 * </ul>
 */
@Service
public class JobResumeServiceImpl implements JobResumeService {

    private static final Logger log = LoggerFactory.getLogger(JobResumeServiceImpl.class);
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    private final JobResumeMapper resumeMapper;
    private final JobResumeVersionMapper versionMapper;
    private final JobProperties jobProperties;
    private final PrivacyMasker privacyMasker;
    private final JobVectorService vectorService;

    public JobResumeServiceImpl(JobResumeMapper resumeMapper,
                                JobResumeVersionMapper versionMapper,
                                JobProperties jobProperties,
                                PrivacyMasker privacyMasker,
                                JobVectorService vectorService) {
        this.resumeMapper = resumeMapper;
        this.versionMapper = versionMapper;
        this.jobProperties = jobProperties;
        this.privacyMasker = privacyMasker;
        this.vectorService = vectorService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResume create(Long userId, JobDtos.ResumeSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        String content = requireContent(request.getContent());
        JobResume resume = new JobResume();
        resume.setUserId(userId);
        resume.setTitle(defaultTitle(request.getTitle()));
        resume.setCandidateName(nvl(request.getCandidateName()));
        resume.setTargetPosition(nvl(request.getTargetPosition()));
        resume.setSummary(nvl(request.getSummary()));
        resume.setContent(content);
        resume.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        resume.setCurrentVersion(1);
        resumeMapper.insert(resume);
        // 初始版本快照
        insertVersion(resume, 1, "初始版本");
        // 写入 Milvus 向量库（Milvus 不可用时内部降级，不影响主流程）
        vectorService.indexResume(resume);
        log.info("创建简历成功:userId={},resumeId={},标题={}", userId, resume.getId(), resume.getTitle());
        return resume;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResume update(Long userId, Long id, JobDtos.ResumeSaveRequest request) {
        JobResume existing = requireResume(userId, id);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        boolean contentProvided = StringUtils.hasText(request.getContent());
        String newContent = contentProvided ? requireContent(request.getContent()) : existing.getContent();
        boolean contentChanged = !Objects.equals(normalize(existing.getContent()), normalize(newContent));

        JobResume update = new JobResume();
        update.setId(existing.getId());
        update.setUserId(userId);
        update.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : existing.getTitle());
        update.setCandidateName(request.getCandidateName() == null ? existing.getCandidateName() : request.getCandidateName().trim());
        update.setTargetPosition(request.getTargetPosition() == null ? existing.getTargetPosition() : request.getTargetPosition().trim());
        update.setSummary(request.getSummary() == null ? existing.getSummary() : request.getSummary().trim());
        update.setContent(newContent);
        update.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        int rows = resumeMapper.update(update);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        if (contentChanged) {
            int nextVersion = nextVersionNo(id);
            insertVersion(update, nextVersion, "编辑自动保存");
            resumeMapper.updateCurrentVersion(id, userId, nextVersion);
            log.info("简历正文变更,已生成版本:resumeId={},version={}", id, nextVersion);
        }
        JobResume latest = detail(userId, id);
        // 正文变化后重建向量索引，保证 RAG 检索到的是最新内容
        if (contentChanged) {
            vectorService.indexResume(latest);
        }
        return latest;
    }

    @Override
    public JobResume detail(Long userId, Long id) {
        return requireResume(userId, id);
    }

    @Override
    public ViewDtos.PageResult<JobResume> page(Long userId, String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = normalizeSize(size);
        int offset = (safePage - 1) * safeSize;
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<JobResume> list = resumeMapper.selectByPage(userId, safeKeyword, offset, safeSize);
        int total = resumeMapper.countByPage(userId, safeKeyword);
        ViewDtos.PageResult<JobResume> result = ViewDtos.PageResult.of(list, total, safePage, safeSize);
        result.setKeyword(safeKeyword);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        int rows = resumeMapper.softDelete(id, userId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        // 同步删除 Milvus 中该简历的向量分块，避免检索到已删除内容
        vectorService.deleteSource(JobVectorServiceImpl.SOURCE_RESUME, id);
        log.info("删除简历:userId={},resumeId={}", userId, id);
    }

    @Override
    public List<JobResumeVersion> versions(Long userId, Long id) {
        requireResume(userId, id);
        return versionMapper.selectByResumeId(id);
    }

    @Override
    public JobResumeVersion versionDetail(Long userId, Long id, int versionNo) {
        requireResume(userId, id);
        JobResumeVersion version = versionMapper.selectByVersionNo(id, versionNo);
        if (version == null) {
            throw new BusinessException(ErrorCode.VERSION_NOT_FOUND);
        }
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResumeVersion saveVersion(Long userId, Long id, String changeNote) {
        JobResume resume = requireResume(userId, id);
        int nextVersion = nextVersionNo(id);
        String note = StringUtils.hasText(changeNote) ? changeNote.trim() : "手动保存版本";
        JobResumeVersion version = insertVersion(resume, nextVersion, note);
        resumeMapper.updateCurrentVersion(id, userId, nextVersion);
        log.info("手动保存简历版本:resumeId={},version={},note={}", id, nextVersion, note);
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResume restoreVersion(Long userId, Long id, int versionNo) {
        JobResume resume = requireResume(userId, id);
        JobResumeVersion version = versionMapper.selectByVersionNo(id, versionNo);
        if (version == null) {
            throw new BusinessException(ErrorCode.VERSION_NOT_FOUND);
        }
        JobResume update = new JobResume();
        update.setId(resume.getId());
        update.setUserId(userId);
        update.setTitle(version.getTitle());
        update.setCandidateName(resume.getCandidateName());
        update.setTargetPosition(resume.getTargetPosition());
        update.setSummary(resume.getSummary());
        update.setContent(version.getContent());
        update.setStatus(resume.getStatus());
        resumeMapper.update(update);
        // 回滚也生成新版本，保证历史可追溯
        int nextVersion = nextVersionNo(id);
        insertVersion(update, nextVersion, "回滚至版本 v" + versionNo);
        resumeMapper.updateCurrentVersion(id, userId, nextVersion);
        log.info("回滚简历版本:resumeId={},from={},new={}", id, versionNo, nextVersion);
        JobResume restored = detail(userId, id);
        vectorService.indexResume(restored);
        return restored;
    }

    @Override
    public String importText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > jobProperties.getUpload().getMaxFileSize()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY.getCode(),
                    "文件超过大小限制(" + jobProperties.getUpload().getMaxFileSize() / 1024 + "KB)");
        }
        String filename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!filename.endsWith(".txt") && !filename.endsWith(".md")) {
            throw new BusinessException(ErrorCode.FILE_TYPE_ERROR);
        }
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ErrorCode.FILE_EMPTY);
            }
            PrivacyMasker.MaskResult masked = privacyMasker.maskWithReport(text);
            log.info("导入简历文本:文件名={},长度={},脱敏{}处", filename, text.length(), masked.maskedCount());
            return masked.text();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_EMPTY.getCode(), "读取文件失败:" + e.getMessage());
        }
    }

    /** 校验正文非空、长度并脱敏 */
    private String requireContent(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            throw new BusinessException(ErrorCode.RESUME_CONTENT_EMPTY);
        }
        String content = privacyMasker.mask(rawContent.trim());
        int max = jobProperties.getResume().getMaxContentLength();
        if (content.length() > max) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(),
                    "简历正文超过长度限制(" + max + "字符)");
        }
        return content;
    }

    private JobResume requireResume(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        JobResume resume = resumeMapper.selectById(id, userId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        return resume;
    }

    private JobResumeVersion insertVersion(JobResume resume, int versionNo, String note) {
        JobResumeVersion version = new JobResumeVersion();
        version.setResumeId(resume.getId());
        version.setVersionNo(versionNo);
        version.setTitle(resume.getTitle());
        version.setContent(resume.getContent());
        version.setChangeNote(note);
        versionMapper.insert(version);
        return version;
    }

    private int nextVersionNo(Long resumeId) {
        Integer max = versionMapper.selectMaxVersionNo(resumeId);
        return max == null ? 1 : max + 1;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return jobProperties.getResume().getDefaultPageSize();
        }
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    private String defaultTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : "未命名简历";
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String content) {
        return content == null ? "" : content.trim();
    }
}
