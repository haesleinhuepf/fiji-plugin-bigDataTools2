import de.embl.cba.bigDataTools2.dataStreamingGUI.BigDataConverter;
import de.embl.cba.bigDataTools2.fileInfoSource.FileInfoConstants;
import de.embl.cba.bigDataTools2.viewers.ViewerUtils;

public class TestBdvViewer
{
	public static void main( String[] args )
	{
		BigDataConverter bigDataConverter = new BigDataConverter();

		String imageDirectory = TestBdvViewer.class.getResource( ""  ).toString();

		bigDataConverter.openFromDirectory(
				imageDirectory.toString(),
				FileInfoConstants.SINGLE_CHANNEL_TIMELAPSE,
				".*",
				null,
				ViewerUtils.getImageViewer( ViewerUtils.BIG_DATA_VIEWER ) );
	}
}
