import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import javax.sound.sampled.*;


public class Station extends UnicastRemoteObject implements IStation {
    private final String stationName;
    private volatile boolean tonePlaying = false;
    private Clip currentClip = null;
    private int pauseFramePosition = 0;

    public Station(String stationName) throws RemoteException {
        super();
        this.stationName = stationName;
    }

    @Override
    public synchronized void playBirdSong(int songId) throws RemoteException {
        stopCurrentClipIfPlaying();

        System.out.println("[" + stationName + "] Tocando canto de pássaro ID: " + songId);
        String caminho = "audios" + File.separator + "bird" + songId + ".wav";
        File arquivo = new File(caminho);

        if (!arquivo.exists()) {
            System.err.println("Arquivo não encontrado: " + caminho);
            return;
        }

        try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(arquivo)) {
            AudioFormat format = audioIn.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);

            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(audioIn);
            currentClip = clip;
            pauseFramePosition = 0;

            clip.start();

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    if (clip.getFramePosition() >= clip.getFrameLength()) {
                        clip.close();
                        currentClip = null;
                        pauseFramePosition = 0;
                        System.out.println("[" + stationName + "] Reprodução encerrada.");
                    }
                }
            });

        } catch (UnsupportedAudioFileException | LineUnavailableException | IOException ex) {
            System.err.println("Erro ao tocar áudio: " + ex.getMessage());
        }
    }

    @Override
    public synchronized void pauseAudio() throws RemoteException {
        if (currentClip != null && currentClip.isRunning()) {
            pauseFramePosition = currentClip.getFramePosition();
            currentClip.stop();
            System.out.println("[" + stationName + "] Áudio pausado.");
        } else {
            System.out.println("[" + stationName + "] Nenhum áudio ativo para pausar.");
        }
    }

    @Override
    public synchronized void resumeAudio() throws RemoteException {
        if (currentClip != null && !currentClip.isRunning()) {
            currentClip.setFramePosition(pauseFramePosition);
            currentClip.start();
            System.out.println("[" + stationName + "] Áudio retomado.");
        } else {
            System.out.println("[" + stationName + "] Nenhum áudio pausado para retomar.");
        }
    }

    @Override
    public void changeSoundPattern(String pattern) throws RemoteException {
        tonePlaying = false;
        if (pattern == null || pattern.trim().isEmpty()) {
            System.out.println("[" + stationName + "] Padrão vazio recebido.");
            return;
        }

        Thread toneThread = new Thread(() -> {
            tonePlaying = true;
            try {
                int sum = 0;
                for (char c : pattern.toCharArray()) sum += c;
                float frequency = 200 + (sum % 801);
                float sampleRate = 44100;
                int numSamples = (int) (10 * sampleRate);
                byte[] buffer = new byte[2 * numSamples];

                for (int i = 0; i < numSamples; i++) {
                    double angle = 2.0 * Math.PI * i * frequency / sampleRate;
                    short value = (short) (Math.sin(angle) * Short.MAX_VALUE);
                    buffer[2 * i] = (byte) (value & 0xff);
                    buffer[2 * i + 1] = (byte) ((value >> 8) & 0xff);
                }

                AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                    line.open(format);
                    line.start();
                    for (int i = 0; i < buffer.length && tonePlaying; i += 4096) {
                        int len = Math.min(4096, buffer.length - i);
                        line.write(buffer, i, len);
                    }
                    line.drain();
                    line.stop();
                    line.close();
                }

            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        });

        toneThread.start();
    }

    private void stopCurrentClipIfPlaying() {
        if (currentClip != null && currentClip.isOpen()) {
            currentClip.stop();
            currentClip.close();
            currentClip = null;
            pauseFramePosition = 0;
        }
    }
}