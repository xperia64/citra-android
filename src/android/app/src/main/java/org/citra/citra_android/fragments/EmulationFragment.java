package org.citra.citra_android.fragments;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.citra.citra_android.NativeLibrary;
import org.citra.citra_android.R;
import org.citra.citra_android.activities.EmulationActivity;
import org.citra.citra_android.overlay.InputOverlay;
import org.citra.citra_android.services.DirectoryInitializationService;
import org.citra.citra_android.services.DirectoryInitializationService.DirectoryInitializationState;
import org.citra.citra_android.utils.DirectoryStateReceiver;
import org.citra.citra_android.utils.Log;

public final class EmulationFragment extends Fragment implements SurfaceHolder.Callback
{
  private static final String KEY_GAMEPATH = "gamepath";

  private static final Handler perfStatsUpdateHandler = new Handler();

  private SharedPreferences mPreferences;

  private InputOverlay mInputOverlay;

  private EmulationState mEmulationState;

  private DirectoryStateReceiver directoryStateReceiver;

  private EmulationActivity activity;

  private TextView mPerfStats;

  private Runnable perfStatsUpdater;

  public static EmulationFragment newInstance(String gamePath)
  {

    Bundle args = new Bundle();
    args.putString(KEY_GAMEPATH, gamePath);

    EmulationFragment fragment = new EmulationFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onAttach(Context context)
  {
    super.onAttach(context);

    if (context instanceof EmulationActivity)
    {
      activity = (EmulationActivity) context;
      NativeLibrary.setEmulationActivity((EmulationActivity) context);
    }
    else
    {
      throw new IllegalStateException("EmulationFragment must have EmulationActivity parent");
    }
  }

  /**
   * Initialize anything that doesn't depend on the layout / views in here.
   */
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    // So this fragment doesn't restart on configuration changes; i.e. rotation.
    setRetainInstance(true);

    mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

    String gamePath = getArguments().getString(KEY_GAMEPATH);
    mEmulationState = new EmulationState(gamePath);
  }

  /**
   * Initialize the UI and start emulation in here.
   */
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
  {
    View contents = inflater.inflate(R.layout.fragment_emulation, container, false);

    SurfaceView surfaceView = contents.findViewById(R.id.surface_emulation);
    surfaceView.getHolder().addCallback(this);

    mInputOverlay = contents.findViewById(R.id.surface_input_overlay);
    if (mInputOverlay != null)
    {
      // If the input overlay was previously disabled, then don't show it.
      if (!mPreferences.getBoolean("showInputOverlay", true))
      {
        mInputOverlay.setVisibility(View.GONE);
      }
    }

    Button doneButton = contents.findViewById(R.id.done_control_config);
    if (doneButton != null)
    {
      doneButton.setOnClickListener(v -> stopConfiguringControls());
    }

    mPerfStats = contents.findViewById(R.id.perf_stats_text);
    if (mPerfStats != null)
    {
      // If the overlay was previously disabled, then don't show it.
      if (!mPreferences.getBoolean("showPerfStats", true))
      {
        mPerfStats.setVisibility(View.GONE);
      }
      else
      {
        updatePerfStats();
      }
    }

    // The new Surface created here will get passed to the native code via onSurfaceChanged.

    return contents;
  }

  @Override
  public void onResume()
  {
    super.onResume();
    if (DirectoryInitializationService.areDolphinDirectoriesReady())
    {
      mEmulationState.run(activity.isActivityRecreated());
    }
    else
    {
      setupDolphinDirectoriesThenStartEmulation();
    }
  }

  @Override
  public void onPause()
  {
    if (directoryStateReceiver != null)
    {
      LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(directoryStateReceiver);
      directoryStateReceiver = null;
    }

    mEmulationState.pause();
    super.onPause();
  }

  @Override
  public void onDetach()
  {
    NativeLibrary.clearEmulationActivity();
    super.onDetach();
  }

  private void setupDolphinDirectoriesThenStartEmulation()
  {
    IntentFilter statusIntentFilter = new IntentFilter(
            DirectoryInitializationService.BROADCAST_ACTION);

    directoryStateReceiver =
            new DirectoryStateReceiver(directoryInitializationState ->
            {
              if (directoryInitializationState ==
                      DirectoryInitializationState.DOLPHIN_DIRECTORIES_INITIALIZED)
              {
                mEmulationState.run(activity.isActivityRecreated());
              }
              else if (directoryInitializationState ==
                      DirectoryInitializationState.EXTERNAL_STORAGE_PERMISSION_NEEDED)
              {
                Toast.makeText(getContext(), R.string.write_permission_needed, Toast.LENGTH_SHORT)
                        .show();
              }
              else if (directoryInitializationState ==
                      DirectoryInitializationState.CANT_FIND_EXTERNAL_STORAGE)
              {
                Toast.makeText(getContext(), R.string.external_storage_not_mounted,
                        Toast.LENGTH_SHORT)
                        .show();
              }
            });

    // Registers the DirectoryStateReceiver and its intent filters
    LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
            directoryStateReceiver,
            statusIntentFilter);
    DirectoryInitializationService.startService(getActivity());
  }

  public void toggleInputOverlayVisibility()
  {
    SharedPreferences.Editor editor = mPreferences.edit();

    // If the overlay is currently set to INVISIBLE
    if (!mPreferences.getBoolean("showInputOverlay", false))
    {
      // Set it to VISIBLE
      mInputOverlay.setVisibility(View.VISIBLE);
      editor.putBoolean("showInputOverlay", true);
    }
    else
    {
      // Set it to INVISIBLE
      mInputOverlay.setVisibility(View.GONE);
      editor.putBoolean("showInputOverlay", false);
    }

    editor.apply();
  }

  public void refreshInputOverlay()
  {
    mInputOverlay.refreshControls();
  }

  public void resetInputOverlay()
  {
    mInputOverlay.resetButtonPlacement();
  }

  public void togglePerfStatsVisibility()
  {
    SharedPreferences.Editor editor = mPreferences.edit();

    // If the overlay is currently set to INVISIBLE
    if (!mPreferences.getBoolean("showPerfStats", false))
    {
      updatePerfStats();
      // Set it to VISIBLE
      mPerfStats.setVisibility(View.VISIBLE);
      editor.putBoolean("showPerfStats", true);
    }
    else
    {
      stopPerfStatsUpdates();
      // Set it to INVISIBLE
      mPerfStats.setVisibility(View.GONE);
      editor.putBoolean("showPerfStats", false);
    }

    editor.apply();
  }

  private void updatePerfStats()
  {
    final int SYSTEM_FPS = 0;
    final int FPS = 1;
    final int FRAMETIME = 2;
    final int SPEED = 3;

    perfStatsUpdater = () ->
    {
      double perfStats[] = NativeLibrary.GetPerfStats();
      mPerfStats
              .setText(String.format("FPS: %.5s\nFrametime: %.7sms\nSpeed: %.4s%%", perfStats[FPS],
                      perfStats[FRAMETIME] * 1000.0, perfStats[SPEED] * 100.0));

      perfStatsUpdateHandler.postDelayed(perfStatsUpdater, 3000 /* 3s */);
    };
    perfStatsUpdateHandler.post(perfStatsUpdater);
  }

  private void stopPerfStatsUpdates()
  {
    if (perfStatsUpdater != null)
    {
      perfStatsUpdateHandler.removeCallbacks(perfStatsUpdater);
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder)
  {
    // We purposely don't do anything here.
    // All work is done in surfaceChanged, which we are guaranteed to get even for surface creation.
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
  {
    Log.debug("[EmulationFragment] Surface changed. Resolution: " + width + "x" + height);
    mEmulationState.newSurface(holder.getSurface());
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder)
  {
    mEmulationState.clearSurface();
  }

  public void stopEmulation()
  {
    mEmulationState.stop();
  }

  public void startConfiguringControls()
  {
    getView().findViewById(R.id.done_control_config).setVisibility(View.VISIBLE);
    mInputOverlay.setIsInEditMode(true);
  }

  public void stopConfiguringControls()
  {
    getView().findViewById(R.id.done_control_config).setVisibility(View.GONE);
    mInputOverlay.setIsInEditMode(false);
  }

  public boolean isConfiguringControls()
  {
    return mInputOverlay.isInEditMode();
  }

  private static class EmulationState
  {
    private enum State
    {
      STOPPED, RUNNING, PAUSED
    }

    private final String mGamePath;
    private Thread mEmulationThread;
    private State state;
    private Surface mSurface;
    private boolean mRunWhenSurfaceIsValid;

    EmulationState(String gamePath)
    {
      mGamePath = gamePath;
      // Starting state is stopped.
      state = State.STOPPED;
    }

    // Getters for the current state

    public synchronized boolean isStopped()
    {
      return state == State.STOPPED;
    }

    public synchronized boolean isPaused()
    {
      return state == State.PAUSED;
    }

    public synchronized boolean isRunning()
    {
      return state == State.RUNNING;
    }

    // State changing methods

    public synchronized void stop()
    {
      if (state != State.STOPPED)
      {
        Log.debug("[EmulationFragment] Stopping emulation.");
        state = State.STOPPED;
        NativeLibrary.StopEmulation();
      }
      else
      {
        Log.warning("[EmulationFragment] Stop called while already stopped.");
      }
    }

    public synchronized void pause()
    {
      if (state != State.PAUSED)
      {
        state = State.PAUSED;
        Log.debug("[EmulationFragment] Pausing emulation.");

        // Release the surface before pausing, since emulation has to be running for that.
        NativeLibrary.SurfaceDestroyed();
        NativeLibrary.PauseEmulation();
      }
      else
      {
        Log.warning("[EmulationFragment] Pause called while already paused.");
      }
    }

    public synchronized void run(boolean isActivityRecreated)
    {
      if (isActivityRecreated)
      {
        if (NativeLibrary.IsRunning())
        {
          state = State.PAUSED;
        }
      }
      else
      {
        Log.debug("[EmulationFragment] activity resumed or fresh start");
      }

      // If the surface is set, run now. Otherwise, wait for it to get set.
      if (mSurface != null)
      {
        runWithValidSurface();
      }
      else
      {
        mRunWhenSurfaceIsValid = true;
      }
    }

    // Surface callbacks
    public synchronized void newSurface(Surface surface)
    {
      mSurface = surface;
      if (mRunWhenSurfaceIsValid)
      {
        runWithValidSurface();
      }
    }

    public synchronized void clearSurface()
    {
      if (mSurface == null)
      {
        Log.warning("[EmulationFragment] clearSurface called, but surface already null.");
      }
      else
      {
        mSurface = null;
        Log.debug("[EmulationFragment] Surface destroyed.");

        if (state == State.RUNNING)
        {
          NativeLibrary.SurfaceDestroyed();
          state = State.PAUSED;
        }
        else if (state == State.PAUSED)
        {
          Log.warning("[EmulationFragment] Surface cleared while emulation paused.");
        }
        else
        {
          Log.warning("[EmulationFragment] Surface cleared while emulation stopped.");
        }
      }
    }

    private void runWithValidSurface()
    {
      mRunWhenSurfaceIsValid = false;
      if (state == State.STOPPED)
      {
        mEmulationThread = new Thread(() ->
        {
          NativeLibrary.SurfaceChanged(mSurface);
            Log.debug("[EmulationFragment] Starting emulation thread.");
            NativeLibrary.Run(mGamePath);
        }, "NativeEmulation");
        mEmulationThread.start();

      }
      else if (state == State.PAUSED)
      {
        Log.debug("[EmulationFragment] Resuming emulation.");
        NativeLibrary.SurfaceChanged(mSurface);
        NativeLibrary.UnPauseEmulation();
      }
      else
      {
        Log.debug("[EmulationFragment] Bug, run called while already running.");
      }
      state = State.RUNNING;
    }
  }
}
