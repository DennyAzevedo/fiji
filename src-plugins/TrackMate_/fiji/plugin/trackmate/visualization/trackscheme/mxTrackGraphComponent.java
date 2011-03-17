package fiji.plugin.trackmate.visualization.trackscheme;

import static fiji.plugin.trackmate.gui.TrackMateFrame.FONT;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jgrapht.graph.DefaultWeightedEdge;

import com.mxgraph.canvas.mxGraphics2DCanvas;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.handler.mxGraphHandler;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxPoint;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;

import fiji.plugin.trackmate.Feature;
import fiji.plugin.trackmate.Spot;

public class mxTrackGraphComponent extends mxGraphComponent implements mxIEventListener {
	
	private static final long serialVersionUID = -281620557095353617L;
	private static final Color BACKGROUND_COLOR_1 	= Color.GRAY;
	private static final Color BACKGROUND_COLOR_2 	= Color.LIGHT_GRAY;
	private static final Color LINE_COLOR 			= Color.BLACK;
	
	private TreeSet<Float> instants;
	private TreeMap<Float, Integer> rows;
	private int[] columnWidths = null;
	private Color[] columnColors;
	private TrackSchemeFrame frame;

	public mxTrackGraphComponent(TrackSchemeFrame frame) {
		super(frame.getGraph());
		this.frame = frame;
		getViewport().setOpaque(true);
		getViewport().setBackground(BACKGROUND_COLOR_1);
		setZoomFactor(2.0);

		instants = new TreeSet<Float>();
		for (Spot s : frame.trackGraph.vertexSet())
			instants.add(s.getFeature(Feature.POSITION_T));
		
		connectionHandler.addListener(mxEvent.CONNECT, this);
		
		mxGraphics2DCanvas.putShape(mxScaledLabelShape.SHAPE_NAME, new mxScaledLabelShape());
	}
	
	
	/**
	 * Custom {@link mxGraphHandler} so as to avoid clearing the selection when right-clicking elsewhere than
	 * on a cell, which is reserved for aimed at displaying a popup menu.
	 */
	@Override
	protected mxGraphHandler createGraphHandler() {
		return new mxGraphHandler(this) {
			
			public void mousePressed(MouseEvent e)	{
				if (graphComponent.isEnabled() && isEnabled() && !e.isConsumed() && !graphComponent.isForceMarqueeEvent(e)) {
					cell = graphComponent.getCellAt(e.getX(), e.getY(), false);
					initialCell = cell;

					if (cell != null) {
						if (isSelectEnabled() && !graphComponent.getGraph().isCellSelected(cell)) {
							graphComponent.selectCellForEvent(cell, e);
							cell = null;
						}

						// Starts move if the cell under the mouse is movable and/or any
						// cells of the selection are movable
						if (isMoveEnabled() && !e.isPopupTrigger())	{
							start(e);
							e.consume();
						}
					}
				}
			}
			
			/**
			 * 
			 */
			public void mouseReleased(MouseEvent e) {
				if (graphComponent.isEnabled() && isEnabled() && !e.isConsumed()) {
					mxGraph graph = graphComponent.getGraph();
					double dx = 0;
					double dy = 0;

					if (first != null && (cellBounds != null || movePreview.isActive())) {
						double scale = graph.getView().getScale();
						mxPoint trans = graph.getView().getTranslate();

						// TODO: Simplify math below, this was copy pasted from
						// getPreviewLocation with the rounding removed
						dx = e.getX() - first.x;
						dy = e.getY() - first.y;

						if (cellBounds != null)
						{
							double dxg = ((cellBounds.getX() + dx) / scale)	- trans.getX();
							double dyg = ((cellBounds.getY() + dy) / scale)	- trans.getY();

							double x = ((dxg + trans.getX()) * scale) + (bbox.getX()) - (cellBounds.getX());
							double y = ((dyg + trans.getY()) * scale) + (bbox.getY()) - (cellBounds.getY());

							dx = Math.round((x - bbox.getX()) / scale);
							dy = Math.round((y - bbox.getY()) / scale);
						}
					}

					if (first == null || !graphComponent.isSignificant(e.getX() - first.x, e.getY() - first.y)) {
						// Delayed handling of selection
						if (cell != null && !e.isPopupTrigger() && isSelectEnabled() && (first != null || !isMoveEnabled())) {
							graphComponent.selectCellForEvent(cell, e);
						}

						// Delayed folding for cell that was initially under the mouse
						if (graphComponent.isFoldingEnabled() && graphComponent.hitFoldingIcon(initialCell, e.getX(), e.getY())) {
							fold(initialCell);
						} else {
							// Handles selection if no cell was initially under the mouse
							Object tmp = graphComponent.getCellAt(e.getX(), e.getY(), graphComponent.isSwimlaneSelectionEnabled());

							if (cell == null && first == null) {
								if (tmp == null && e.getButton() == MouseEvent.BUTTON1)  {
									graph.clearSelection(); // JYT I did this to keep selection even if we right-click elsewhere
								}
								else if (graph.isSwimlane(tmp)	
										&& graphComponent.getCanvas().hitSwimlaneContent(graphComponent, graph.getView().getState(tmp),	e.getX(), e.getY())) {
									graphComponent.selectCellForEvent(tmp, e);
								}
							}

							if (graphComponent.isFoldingEnabled() && graphComponent.hitFoldingIcon(tmp, e.getX(), e.getY())) {
								fold(tmp);
								e.consume();
							}
						}
					} else if (movePreview.isActive()) {
						if (graphComponent.isConstrainedEvent(e)) {
							if (Math.abs(dx) > Math.abs(dy)) {
								dy = 0;
							} else {
								dx = 0;
							}
						}

						mxCellState markedState = marker.getMarkedState();
						Object target = (markedState != null) ? markedState.getCell() : null;

						// FIXME: Cell is null if selection was carried out, need other variable
						//trace("cell", cell);

						if (target == null && isRemoveCellsFromParent()	
								&& shouldRemoveCellFromParent(graph.getModel().getParent(initialCell), cells, e)) {
							target = graph.getDefaultParent();
						}

						boolean clone = isCloneEnabled() && graphComponent.isCloneEvent(e);
						Object[] result = movePreview.stop(true, e, dx, dy, clone, target);

						if (cells != result) {
							graph.setSelectionCells(result);
						}

						e.consume();
					}
					else if (isVisible()) {
						if (constrainedEvent)
						{
							if (Math.abs(dx) > Math.abs(dy))
							{
								dy = 0;
							}
							else
							{
								dx = 0;
							}
						}

						mxCellState targetState = marker.getValidState();
						Object target = (targetState != null) ? targetState.getCell()
								: null;

						if (graph.isSplitEnabled()
								&& graph.isSplitTarget(target, cells))
						{
							graph.splitEdge(target, cells, dx, dy);
						}
						else
						{
							moveCells(cells, dx, dy, target, e);
						}

						e.consume();
					}
				}

				reset();
			}
		};
	}
	
	
	
	/**
	 * Override this so as to paint the background with colored rows and columns. 
	 */
	@Override
	public void paintBackground(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;
		Rectangle paintBounds = g.getClipBounds();
		
		int width = getViewport().getView().getSize().width;
		int height = getViewport().getView().getSize().height;
		float scale = (float) graph.getView().getScale();

		// Scaled sizes
		int xcs 			= Math.round(TrackSchemeFrame.X_COLUMN_SIZE*scale);
		int ycs 			= Math.round(TrackSchemeFrame.Y_COLUMN_SIZE*scale);

		// Alternating row color
		g.setColor(BACKGROUND_COLOR_2);
		int y = 0;
		while (y < height) {
			if (y > paintBounds.y - ycs && y < paintBounds.y + paintBounds.height)
				g.fillRect(0, y, width, ycs);
			y += 2*ycs;
		}

		// Header separator
		g.setColor(LINE_COLOR);
		if (ycs > paintBounds.y && ycs < paintBounds.y + paintBounds.height)
			g.drawLine(paintBounds.x, ycs, paintBounds.x + paintBounds.width, ycs);
		if (xcs > paintBounds.x && xcs < paintBounds.x + paintBounds.width)
			g.drawLine(xcs, paintBounds.y, xcs, paintBounds.y + paintBounds.height);

		// Row headers
		int x = xcs / 4;
		y = 3 * ycs / 2;
		g.setFont(FONT.deriveFont(12*scale).deriveFont(Font.BOLD));
		
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for(Float instant : instants) {
			if (xcs > paintBounds.x && y > paintBounds.y - ycs && y < paintBounds.y + paintBounds.height) {
				g.drawString(String.format("%.1f "+frame.settings.timeUnits, instant), x, y);
				g.drawString(String.format("frame %.0f", (instant+1)/frame.settings.dt), x, Math.round(y+12*scale));
			}
			y += ycs;
		}

		// Column headers
		if (null != columnWidths) {
			x = xcs;
			for (int i = 0; i < columnWidths.length; i++) {
				int cw = columnWidths[i]-1;
				g.setColor(columnColors[i]);
				g.drawString(String.format("Track %d", i+1), x+20, ycs/2);
				g.setColor(LINE_COLOR);					
				x += cw * xcs;
				g.drawLine(x, 0, x, height);
			}
		}
	}
	
	public void setRowForInstant(TreeMap<Float, Integer> rowForInstant) {
		rows = rowForInstant;
	}
	
	public TreeMap<Float, Integer> getRowForInstant() {
		return rows;
	}

	public void setColumnWidths(int[] columnWidths) {
		this.columnWidths  = columnWidths;
	}

	public void setColumnColor(Color[] columnColors) {
		this.columnColors = columnColors;
	}

	public int[] getColumnWidths() {
		return columnWidths;
	}

	/** 
	 * This listener method will be invoked when a new edge has been created interactively
	 * in the graph component. 
	 */
	@Override
	public void invoke(Object sender, mxEventObject evt) {
		Map<String, Object> props = evt.getProperties();
		Object obj = (Object) props.get("cell");
		mxCell cell = (mxCell) obj;
		DefaultWeightedEdge edge;
		if (cell.isEdge()) {
			frame.getGraph().getModel().beginUpdate();
			try {
				Spot source = frame.getGraph().getCellToVertexMap().get(cell.getSource());
				Spot target = frame.getGraph().getCellToVertexMap().get(cell.getTarget());
				// We add a new jGraphT edge to the underlying model
				edge = frame.lGraph.addEdge(source, target);
				frame.lGraph.setEdgeWeight(edge, -1);
				// Then, remove the old JGraphX edge.
				frame.getGraph().removeCells(new Object[] { cell });
				evt.consume();
			} finally {
				frame.getGraph().getModel().endUpdate();
			}
			// Then we do the update, and get the new JGraphX edge (through the map in the adapter) and change its value and style. Easy.
			frame.getGraph().getModel().beginUpdate();
			try {
				mxCell newEdgeCell = frame.getGraph().getEdgeToCellMap().get(edge);
				newEdgeCell.setValue("New");
			} finally {
				frame.getGraph().getModel().endUpdate();
			}
		}
	}

}
