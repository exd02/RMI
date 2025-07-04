import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IStation extends Remote {
    void playBirdSong(int songId) throws RemoteException;
    void changeSoundPattern(String pattern) throws RemoteException;
    void pauseAudio() throws RemoteException;
    void resumeAudio() throws RemoteException;
}