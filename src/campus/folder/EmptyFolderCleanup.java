package campus.folder;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** 파일·링크는 삭제하지 않고 빈 디렉터리만 정리한다. Windows 읽기 전용 폴더도 지원한다. */
public final class EmptyFolderCleanup {
    private EmptyFolderCleanup() { }
    public static boolean remove(FolderService service, Path building) throws IOException {
        Path root=service.checked(building);
        if(!service.getRoot().equals(root.getParent()))throw new IOException("건물 폴더만 정리할 수 있습니다: "+root);
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return true;
        List<Path> entries;
        try(var paths=Files.walk(root)) { entries=paths.toList(); }
        catch(UncheckedIOException ex) { throw ex.getCause(); }
        // 숨김 파일, 동기화 메타데이터, 링크도 사용자 파일로 취급하여 보존한다.
        for(Path path:entries)if(!Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isDirectory())return false;
        for(Path path:entries.stream().sorted(Comparator.reverseOrder()).toList()) {
            service.checked(path);
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))continue;
            // 읽기 전용 해제는 삭제 대상이 비어 있음을 다시 확인한 뒤에만 한다.
            try(var contents=Files.newDirectoryStream(path)) { if(contents.iterator().hasNext())return false; }
            DosFileAttributeView dos=Files.getFileAttributeView(path,DosFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            boolean readOnly=dos!=null&&dos.readAttributes().isReadOnly();
            if(readOnly)dos.setReadOnly(false);
            try { Files.delete(path); }
            catch(IOException failure) {
                if(readOnly&&Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {
                    try { dos.setReadOnly(true); } catch(IOException restore) { failure.addSuppressed(restore); }
                }
                throw failure;
            }
        }
        return true;
    }
}
