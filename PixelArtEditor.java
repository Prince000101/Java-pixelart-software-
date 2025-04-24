import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Stack;

public class PixelArtEditor {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(PixelArtFrame::new);
    }
}

class PixelArtFrame extends JFrame {
    private final PixelCanvas canvas;
    private final JButton undoBtn, clearBtn, saveBtn, loadBtn, colorPickerBtn;
    private final JComboBox<Integer> gridSizeCombo;

    public PixelArtFrame() {
        super("Pixel Art Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        canvas = new PixelCanvas(32);
        add(canvas, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        colorPickerBtn = new JButton("Choose Color");
        colorPickerBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Pick Drawing Color", canvas.getCurrentColor());
            if (c != null) canvas.setCurrentColor(c);
        });
        controls.add(colorPickerBtn);

        controls.add(new JLabel("Grid Size:"));
        gridSizeCombo = new JComboBox<>(new Integer[]{8, 16, 32, 64, 128});
        gridSizeCombo.setSelectedItem(32);
        gridSizeCombo.addActionListener(e -> canvas.setGridSize((Integer) gridSizeCombo.getSelectedItem()));
        controls.add(gridSizeCombo);

        undoBtn = new JButton("Undo");
        undoBtn.addActionListener(e -> canvas.undo());
        controls.add(undoBtn);

        clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> canvas.clear());
        controls.add(clearBtn);

        saveBtn = new JButton("Save…");
        saveBtn.addActionListener(e -> canvas.saveToFile());
        controls.add(saveBtn);

        loadBtn = new JButton("Load…");
        loadBtn.addActionListener(e -> canvas.loadFromFile());
        controls.add(loadBtn);

        add(controls, BorderLayout.NORTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
}

class PixelCanvas extends JPanel {
    private BufferedImage image;
    private int gridSize;
    private Color currentColor = Color.BLACK;
    private final Stack<BufferedImage> undoStack = new Stack<>();

    public PixelCanvas(int initialGrid) {
        this.gridSize = initialGrid;
        setPreferredSize(new Dimension(512, 512));
        setBorder(new LineBorder(Color.GRAY));
        initImage();

        MouseAdapter ma = new MouseAdapter() {
            private boolean drawing = false;
            private int button;

            @Override
            public void mousePressed(MouseEvent e) {
                saveStateForUndo();
                drawing = true;
                button = e.getButton();
                drawAt(e.getX(), e.getY(), button);
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (drawing) drawAt(e.getX(), e.getY(), button);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                drawing = false;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private void initImage() {
        image = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_ARGB);
        clear();
    }

    public void setGridSize(int newSize) {
        this.gridSize = newSize;
        initImage();
        repaint();
    }

    public Color getCurrentColor() { return currentColor; }
    public void setCurrentColor(Color c) { this.currentColor = c; }

    public void clear() {
        saveStateForUndo();
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, gridSize, gridSize);
        g.dispose();
        repaint();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            image = undoStack.pop();
            repaint();
        }
    }

    private void saveStateForUndo() {
        BufferedImage copy = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        undoStack.push(copy);
        if (undoStack.size() > 20) undoStack.remove(0);
    }

    private void drawAt(int mx, int my, int button) {
        int w = getWidth(), h = getHeight();
        int cellSize = Math.max(w, h) / gridSize;

        int px = mx / cellSize;
        int py = my / cellSize;
        if (px < 0 || px >= gridSize || py < 0 || py >= gridSize) return;

        int rgb = (button == MouseEvent.BUTTON3)
                ? Color.WHITE.getRGB()
                : currentColor.getRGB();
        image.setRGB(px, py, rgb);
        repaint(px * cellSize, py * cellSize, cellSize, cellSize);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth(), h = getHeight();
        int cellSize = Math.max(w, h) / gridSize;
        int drawW = cellSize * gridSize, drawH = cellSize * gridSize;

        g.drawImage(image, 0, 0, drawW, drawH, 0, 0, gridSize, gridSize, null);
        g.setColor(new Color(0, 0, 0, 50));
        for (int i = 0; i <= gridSize; i++) {
            int x = i * cellSize, y = i * cellSize;
            g.drawLine(x, 0, x, drawH);
            g.drawLine(0, y, drawW, y);
        }
    }

    public void saveToFile() {
        // 1) Ask where to save:
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(
          new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png")
        );
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) {
            f = new File(f.getParentFile(), f.getName() + ".png");
        }
    
        // 2) Get your screen resolution:
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int sw = screen.width, sh = screen.height;
    
        // 3) Compute an integer cell size that fits both dimensions:
        int cellSize = Math.min(sw / gridSize, sh / gridSize);
    
        // 4) Compute where the grid will sit (centered):
        int imgW = cellSize * gridSize;
        int imgH = cellSize * gridSize;
        int offsetX = (sw - imgW) / 2;
        int offsetY = (sh - imgH) / 2;
    
        // 5) Create a full-screen BufferedImage & fill background:
        BufferedImage full = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = full.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, sw, sh);
    
        // 6) Nearest‑neighbor draw your pixel-art into that centered area:
        g2.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );
        g2.drawImage(
          image,
          offsetX, offsetY,               // dest x,y
          offsetX + imgW, offsetY + imgH, // dest x2,y2
          0, 0,                            // src x,y
          gridSize, gridSize,             // src x2,y2
          null
        );
        g2.dispose();
    
        // 7) Write it out as PNG:
        try {
            ImageIO.write(full, "PNG", f);
            JOptionPane.showMessageDialog(
                this,
                "Exported " + imgW + "×" + imgH +
                " grid centered in " + sw + "×" + sh +
                "\nSaved to: " + f.getAbsolutePath()
            );
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                "Error saving full‑screen image:\n" + e.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
        

    public void loadFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try {
            BufferedImage loaded = ImageIO.read(f);
            if (loaded == null) throw new IOException("Not an image");
            saveStateForUndo();

            // Resample back to gridSize×gridSize
            BufferedImage resized = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = resized.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(loaded, 0, 0, gridSize, gridSize, null);
            g2.dispose();

            image = resized;
            repaint();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Error loading image:\n" + e.getMessage(),
                "Load Error", JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
