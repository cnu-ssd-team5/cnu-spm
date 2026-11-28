package campus.folder;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** 건물별 최근 열기 기록. 사용자 자료 폴더와 분리해 저장한다. */
public final class RecentFiles {
    private final FolderService service;
    private final Path building, store;
    public RecentFiles(FolderService service,Path building)throws IOException {
        this.service=service;
        this.building=service.checked(building);
        Path meta=service.getRoot().getParent().resolve(".folder-recent");
        Files.createDirectories(meta);
        store=meta.resolve(Base64.getUrlEncoder().withoutPadding().encodeToString(building.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))+".properties");
    }
    public List<Path> list()throws IOException {
        Properties data=new Properties();
        if(Files.exists(store))try(InputStream in=Files.newInputStream(store)) {
            data.load(in);
        }
        List<Path> result=new ArrayList<>();
        for (int i=0; i<12; i++) {
            String name=data.getProperty("file."+i);
            if(name==null)continue;
            try {
                Path p=service.checked(building.resolve(name));
                if(p.startsWith(building)&&Files.isRegularFile(p)&&!result.contains(p))result.add(p);
            }
            catch(Exception ignored) {
            }
        }
        return result;
    }
    public void record(Path file)throws IOException {
        file=service.checked(file);
        if(!file.startsWith(building)||!Files.isRegularFile(file))return;
        List<Path> files=list();
        files.remove(file);
        files.add(0,file);
        Properties data=new Properties();
        for (int i=0; i<Math.min(12,files.size()); i++)data.setProperty("file."+i,building.relativize(files.get(i)).toString());
        Path tmp=Files.createTempFile(store.getParent(),"recent-",".tmp");
        try {
            try(OutputStream out=Files.newOutputStream(tmp)) {
                data.store(out,"Recent files");
            }
            Files.move(tmp,store,StandardCopyOption.REPLACE_EXISTING);
        }
        finally {
            Files.deleteIfExists(tmp);
        }
    }
}
