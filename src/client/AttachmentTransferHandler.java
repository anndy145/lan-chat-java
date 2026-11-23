package client;

import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/** 接受拖曳或貼上的檔案與剪貼簿圖片，其他資料交給原本的文字處理器。 */
final class AttachmentTransferHandler extends TransferHandler {
    private final TransferHandler delegate;
    private final ChatWindow view;
    private final ChatController controller;

    AttachmentTransferHandler(TransferHandler delegate, ChatWindow view, ChatController controller) {
        this.delegate = delegate;
        this.view = view;
        this.controller = controller;
    }

    private static boolean pastedImage(TransferSupport support) {
        // 試算表等程式會同時提供文字與圖片，此時保留文字貼上。
        return support.isDataFlavorSupported(DataFlavor.imageFlavor)
                && !support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return view.imagesAllowedHere() || view.filesAllowed();
        }
        if (pastedImage(support)) {
            return view.imagesAllowedHere();
        }
        return delegate != null && delegate.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        try {
            var transferable = support.getTransferable();
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                if (!view.imagesAllowedHere() && !view.filesAllowed()) return false;
                List<?> files = (List<?>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths = files.stream().filter(File.class::isInstance)
                        .map(file -> ((File) file).toPath()).toList();
                // 延後顯示確認視窗，讓拖放操作先完成。
                SwingUtilities.invokeLater(() -> controller.sendDroppedFiles(paths));
                return true;
            }
            if (pastedImage(support)) {
                if (!view.imagesAllowedHere()) return false;
                Image image = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                SwingUtilities.invokeLater(() -> controller.sendPastedImage(image));
                return true;
            }
        } catch (UnsupportedFlavorException | IOException | ClassCastException exception) {
            view.showMessage("Could not read the pasted or dropped content.");
            return false;
        }
        return delegate != null && delegate.importData(support);
    }

    @Override
    public int getSourceActions(JComponent component) {
        return delegate == null ? NONE : delegate.getSourceActions(component);
    }

    @Override
    public void exportAsDrag(JComponent component, InputEvent event, int action) {
        if (delegate != null) delegate.exportAsDrag(component, event, action);
    }

    @Override
    public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
        if (delegate != null) delegate.exportToClipboard(component, clipboard, action);
    }
}
