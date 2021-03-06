package com.marginallyclever.makelangeloRobot.converters;

import java.io.IOException;
import java.io.Writer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import com.marginallyclever.makelangeloRobot.TransformedImage;
import com.marginallyclever.makelangeloRobot.loadAndSave.LoadAndSaveImage;
import com.marginallyclever.makelangeloRobot.ImageManipulator;
import com.marginallyclever.makelangeloRobot.MakelangeloRobotDecorator;
import com.marginallyclever.makelangeloRobot.settings.MakelangeloRobotSettings;

/**
 * Converts a BufferedImage to gcode
 * 
 * Image converters have to be listed in 
 * src/main/resources/META-INF/services/com.marginallyclever.makelangeloRobot.generators.ImageConverter
 * in order to be found by the ServiceLoader.  This is so that you could write an independent plugin and 
 * drop it in the same folder as makelangelo software to be "found" by the software.
 * 
 * Don't forget http://www.reverb-marketing.com/wiki/index.php/When_a_new_style_has_been_added_to_the_Makelangelo_software
 * @author Dan Royer
 *
 */
public abstract class ImageConverter extends ImageManipulator implements MakelangeloRobotDecorator {
	protected TransformedImage sourceImage;
	protected LoadAndSaveImage loadAndSave;
	protected boolean keepIterating=false;
	protected Texture texture = null;


	public void setLoadAndSave(LoadAndSaveImage arg0) {
		loadAndSave = arg0;
	}
	
	/**
	 * set the image to be transformed.
	 * @param img the <code>java.awt.image.BufferedImage</code> this filter is using as source material.
	 */
	public void setImage(TransformedImage img) {
		sourceImage=img;
		texture = null;
	}
	
	/**
	 * run one "step" of an iterative image conversion process.
	 * @return true if conversion should iterate again.
	 */
	public boolean iterate() {
		return false;
	}
	
	public void stopIterating() {
		keepIterating=false;
	}
	
	/**
	 * for "run once" converters, return do the entire conversion and write to disk.
	 * for iterative solvers, the iteration is now done, write to disk.
	 * @param out the Writer to receive the generated gcode.
	 */
	public void finish(Writer out) throws IOException {}
	
	/**
	 * @return the gui panel with options for this manipulator
	 */
	public ImageConverterPanel getPanel() {
		return null;
	}

	/**
	 * Live preview as the system is converting pictures.
	 * draw the results as the calculation is being performed.
	 */
	public void render(GL2 gl2, MakelangeloRobotSettings settings) {
		if(texture==null ) {
			if( sourceImage!=null) {
				texture = AWTTextureIO.newTexture(gl2.getGLProfile(), sourceImage.getSourceImage(), false);
			}
		}
		if(texture!=null) {
			double w = sourceImage.getSourceImage().getWidth() * sourceImage.getScaleX();
			double h = sourceImage.getSourceImage().getHeight() * sourceImage.getScaleY();
			gl2.glEnable(GL2.GL_TEXTURE_2D);
			gl2.glEnable(GL2.GL_BLEND);
			gl2.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
			gl2.glDisable(GL2.GL_COLOR);
			gl2.glColor4f(1, 1, 1,0.5f);
			gl2.glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
			texture.bind(gl2);
			gl2.glBegin(GL2.GL_TRIANGLE_FAN);
			gl2.glTexCoord2d(0, 0);	gl2.glVertex2d(-w/2, -h/2 );
			gl2.glTexCoord2d(1, 0);	gl2.glVertex2d( w/2, -h/2 );
			gl2.glTexCoord2d(1, 1);	gl2.glVertex2d( w/2, h/2);
			gl2.glTexCoord2d(0, 1);	gl2.glVertex2d(-w/2, h/2);
			gl2.glEnd();
			gl2.glDisable(GL2.GL_TEXTURE_2D);
			gl2.glDisable(GL2.GL_BLEND);
			gl2.glEnable(GL2.GL_COLOR);
		}	
	}
	

	/**
	 * Drag the pen across the paper from p0 to p1, sampling (p1-p0)/stepSize times.  If the intensity of img
	 * at a sample location is greater than the channelCutff, raise the pen.  Print the gcode results to out.
	 * This method is used by several converters.
	 * 
	 * @param x0 starting position on the paper.
	 * @param y0 starting position on the paper.
	 * @param x1 ending position on the paper.
	 * @param y1 ending position on the paper.
	 * @param stepSize mm level of detail for this line.
	 * @param channelCutoff only put pen down when color below this amount.
	 * @param img the image to sample while converting along the line.
	 * @param out the destination for the gcode generated in the conversion process.
	 * @throws IOException
	 */
	protected void convertAlongLine(double x0,double y0,double x1,double y1,double stepSize,double channelCutoff,TransformedImage img,Writer out) throws IOException {
		double b;
		double dx=x1-x0;
		double dy=y1-y0;
		double halfStep = stepSize/2.0;
		double r2 = Math.sqrt(dx*dx+dy*dy);
		double steps = r2 / stepSize;
		if(steps<1) steps=1;

		double n,x,y,v;

		boolean wasInside = isInsidePaperMargins(x0, y0);
		boolean isInside;
		boolean penUp,oldPenUp;
		double oldX=x0,oldY=y0;
		if(wasInside) {
			v = img.sample( x0 - halfStep, y0 - halfStep, x0 + halfStep, y0 + halfStep);
			oldPenUp = (v>=channelCutoff);
		} else {
			oldPenUp = false;
		}
		
		lineTo(out, x0, y0, true);
		
		for (b = 0; b <= steps; ++b) {
			n = b / steps;
			x = dx * n + x0;
			y = dy * n + y0;
			isInside=isInsidePaperMargins(x, y);
			if(isInside) {
				v = img.sample( x - halfStep, y - halfStep, x + halfStep, y + halfStep);
			} else {
				v = 255;
			}
			penUp = (v>=channelCutoff);
			if(isInside!=wasInside) {
				clipLine(out,oldX,oldY,x,y,oldPenUp,penUp,wasInside,isInside);
			}
			lineTo(out, x, y, penUp);
			if( wasInside && !isInside ) break;  // done
			wasInside=isInside;
			oldX=x;
			oldY=y;
			oldPenUp=penUp;
		}
		lineTo(out, x1, y1, true);
	}
}
