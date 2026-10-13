package com.library.repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * 把一组对象以 JSON 数组的形式存到单个文件的通用仓储基类。
 *
 * <p>读写都用 {@link ReentrantReadWriteLock} 保护：读走读锁，写走写锁，
 * 而 {@link #update} 在整个「读取-修改-写回」期间持有写锁，保证并发下不会丢失更新。
 * 本系统数据量很小，整文件读写完全够用。
 *
 * <p>用的是 Jackson 3（Spring Boot 4 的默认版本），包名为 {@code tools.jackson.*}，
 * 其异常为非受检的 {@link JacksonException}。
 *
 * @param <T> 记录类型
 */
public abstract class JsonFileStore<T> {

    private final ObjectMapper mapper;
    private final Path file;
    private final JavaType listType;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    protected JsonFileStore(ObjectMapper mapper, Path dataDir, String fileName, Class<T> elementType) {
        this.mapper = mapper;
        this.file = dataDir.resolve(fileName);
        this.listType = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建数据目录: " + dataDir, e);
        }
    }

    /** 返回全部记录的快照。 */
    protected List<T> readAll() {
        lock.readLock().lock();
        try {
            return load();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 在写锁内执行「读取-修改-写回」。{@code mutator} 收到的是可变列表，
     * 其返回值会原样返回给调用方；方法结束前列表会被写回文件。
     */
    protected <R> R update(Function<List<T>, R> mutator) {
        lock.writeLock().lock();
        try {
            List<T> items = load();
            R result = mutator.apply(items);
            save(items);
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 数据文件路径，供实现类判断是否需要初始化。 */
    protected Path file() {
        return file;
    }

    private List<T> load() {
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return new ArrayList<>();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取数据文件失败: " + file, e);
        }
        try {
            List<T> items = mapper.readValue(file.toFile(), listType);
            return items == null ? new ArrayList<>() : items;
        } catch (JacksonException e) {
            throw new IllegalStateException("解析数据文件失败: " + file, e);
        }
    }

    private void save(List<T> items) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), items);
        } catch (JacksonException e) {
            throw new IllegalStateException("写入数据文件失败: " + file, e);
        }
    }
}
