#include "doomcraft_bridge.h"

#include "common/console/c_buttons.h"
#include "common/console/c_dispatch.h"
#include "common/console/keydef.h"
#include "common/engine/d_eventbase.h"
#include "common/engine/i_interface.h"
#include "common/engine/gamestate.h"
#include "common/menu/menustate.h"
#include "common/rendering/v_video.h"
#include "common/textures/m_png.h"
#include "d_buttons.h"
#include "v_palette.h"

#include <algorithm>
#include <chrono>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <set>
#include <sstream>
#include <string>
#include <system_error>
#include <vector>

namespace fs = std::filesystem;

/*
 * Mantida pelo backend de entrada do LZDoom.
 *
 * true:
 *   menus, caixas de diálogo e telas GUI esperam EV_GUI_Event.
 *
 * false:
 *   title screen, demo screen e gameplay esperam EV_KeyDown/EV_KeyUp.
 */
extern bool GUICapture;

namespace
{
    constexpr uint32_t FRAME_MAGIC = 0x31464344;
    constexpr uint32_t FRAME_VERSION = 1;
    constexpr uint32_t FRAME_FORMAT_RGBA8 = 1;
    constexpr int OUTPUT_WIDTH = 320;
    constexpr int OUTPUT_HEIGHT = 200;
    constexpr auto FRAME_INTERVAL = std::chrono::milliseconds(28);

    bool initialized = false;
    bool bridgePaused = false;
    uint64_t frameSequence = 0;
    uint64_t inputSequence = 0;

    fs::path bridgeDirectory;
    fs::path commandDirectory;
    fs::path framePath;
    fs::path inputLogPath;

    std::chrono::steady_clock::time_point lastFrameTime{};

    std::string Trim(std::string value)
    {
        while (!value.empty() &&
               std::isspace(static_cast<unsigned char>(value.front())))
        {
            value.erase(value.begin());
        }

        while (!value.empty() &&
               std::isspace(static_cast<unsigned char>(value.back())))
        {
            value.pop_back();
        }

        return value;
    }

    bool SafeToken(const std::string& value)
    {
        return !value.empty() &&
               value.size() <= 96 &&
               std::all_of(
                   value.begin(),
                   value.end(),
                   [](unsigned char character)
                   {
                       return std::isalnum(character) ||
                              character == '_' ||
                              character == '-';
                   }
               );
    }

    void LogInput(const std::string& message)
    {
        if (inputLogPath.empty())
        {
            return;
        }

        std::ofstream output(
            inputLogPath,
            std::ios::out |
            std::ios::app
        );

        if (!output)
        {
            return;
        }

        output
            << ++inputSequence
            << " gui="
            << (GUICapture ? 1 : 0)
            << " gamestate="
            << static_cast<int>(gamestate)
            << " menu="
            << static_cast<int>(menuactive)
            << " "
            << message
            << '\n';
    }

    void Initialize()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        const char* environment =
            std::getenv("DOOMCRAFT_BRIDGE_DIR");

        if (environment == nullptr || *environment == '\0')
        {
            return;
        }

        bridgeDirectory = fs::u8path(environment);
        commandDirectory = bridgeDirectory / "commands";
        framePath = bridgeDirectory / "frame.bin";
        inputLogPath = bridgeDirectory / "bridge-input.log";

        std::error_code error;
        fs::create_directories(commandDirectory, error);

        LogInput("bridge initialized");
    }

    void QueueConsoleCommand(const std::string& command)
    {
        LogInput("console " + command);
        AddCommandString(command.c_str());
    }

    /*
     * Publica uma tecla exatamente como o backend SDL publicaria quando
     * GUICapture está desativado.
     *
     * Isso é indispensável para:
     * - telas iniciais "press any key";
     * - sequência de demonstração;
     * - gameplay e bindings configuráveis.
     */
    void PostNativeKey(
        int keyCode,
        bool pressed,
        int asciiCode = 0
    )
    {
        event_t event{};
        event.type = pressed ? EV_KeyDown : EV_KeyUp;
        event.data1 = static_cast<int16_t>(keyCode);
        event.data2 = static_cast<int16_t>(asciiCode);

        LogInput(
            std::string("native-key ") +
            (pressed ? "down " : "up ") +
            std::to_string(keyCode)
        );

        D_PostEvent(&event);
    }

    /*
     * Publica uma tecla conforme o backend SDL quando GUICapture está ativo.
     */
    void PostGuiKey(int guiKey, bool pressed)
    {
        event_t event{};
        event.type = EV_GUI_Event;
        event.subtype =
            pressed
                ? EV_GUI_KeyDown
                : EV_GUI_KeyUp;
        event.data1 = static_cast<int16_t>(guiKey);
        event.data2 = static_cast<int16_t>(guiKey);
        event.data3 = 0;

        LogInput(
            std::string("gui-key ") +
            (pressed ? "down " : "up ") +
            std::to_string(guiKey)
        );

        D_PostEvent(&event);
    }

    void PostGuiPulse(int guiKey)
    {
        PostGuiKey(guiKey, true);
        PostGuiKey(guiKey, false);
    }

    void PostNativePulse(int keyCode, int asciiCode = 0)
    {
        PostNativeKey(keyCode, true, asciiCode);
        PostNativeKey(keyCode, false, asciiCode);
    }

    void SetPaused(bool desired)
    {
        const bool enginePaused = paused != 0;

        if (desired != enginePaused)
        {
            QueueConsoleCommand("pause");
        }

        bridgePaused = desired;
    }

    constexpr uint16_t DOOMCRAFT_KEY_BASE = 0x6000;

    bool IsGameplayInputContext()
    {
        return gamestate == GS_LEVEL &&
               menuactive == MENU_Off &&
               !GUICapture;
    }

    void SetGameplayButton(
        int buttonIndex,
        bool pressed
    )
    {
        FButtonStatus* button =
            buttonMap.GetButton(buttonIndex);

        if (button == nullptr)
        {
            LogInput(
                "button-map missing " +
                std::to_string(buttonIndex)
            );
            return;
        }

        const uint16_t syntheticKey =
            static_cast<uint16_t>(
                DOOMCRAFT_KEY_BASE +
                buttonIndex
            );

        if (pressed)
        {
            button->PressKey(syntheticKey);
        }
        else
        {
            button->ReleaseKey(syntheticKey);
        }

        LogInput(
            std::string("button-map ") +
            (pressed ? "down " : "up ") +
            std::to_string(buttonIndex)
        );
    }

    void ReleaseAllGameplayButtons()
    {
        const int buttons[] = {
            Button_Forward,
            Button_Back,
            Button_Left,
            Button_Right,
            Button_Attack,
            Button_Use,
            Button_Speed
        };

        for (int buttonIndex : buttons)
        {
            SetGameplayButton(
                buttonIndex,
                false
            );
        }
    }

    void ProcessMenuOrTitleAction(
        const std::string& action,
        bool pressed
    )
    {
        /*
         * Menu aberto: o LZDoom traduz GK_* em MKEY_*.
         */
        if (
            menuactive != MENU_Off ||
            GUICapture
        )
        {
            if (action == "forward")
            {
                PostGuiKey(GK_UP, pressed);
            }
            else if (action == "back")
            {
                PostGuiKey(GK_DOWN, pressed);
            }
            else if (action == "left")
            {
                PostGuiKey(GK_LEFT, pressed);
            }
            else if (action == "right")
            {
                PostGuiKey(GK_RIGHT, pressed);
            }
            else if (
                action == "use" ||
                action == "attack"
            )
            {
                PostGuiKey(
                    GK_RETURN,
                    pressed
                );
            }

            return;
        }

        /*
         * Tela inicial/demonstração: exige EV_KeyDown/EV_KeyUp normal.
         */
        if (action == "forward")
        {
            PostNativeKey(
                KEY_UPARROW,
                pressed
            );
        }
        else if (action == "back")
        {
            PostNativeKey(
                KEY_DOWNARROW,
                pressed
            );
        }
        else if (action == "left")
        {
            PostNativeKey(
                KEY_LEFTARROW,
                pressed
            );
        }
        else if (action == "right")
        {
            PostNativeKey(
                KEY_RIGHTARROW,
                pressed
            );
        }
        else if (action == "attack")
        {
            PostNativeKey(
                KEY_RCTRL,
                pressed
            );
        }
        else if (action == "use")
        {
            PostNativeKey(
                KEY_ENTER,
                pressed,
                GK_RETURN
            );
        }
        else if (action == "speed")
        {
            PostNativeKey(
                KEY_RSHIFT,
                pressed
            );
        }
    }

    /*
     * Dentro da fase, manipula o ButtonMap diretamente. Isso elimina
     * dependência dos bindings gravados no lzdoom.ini e evita que eventos
     * sintéticos de teclado sejam descartados pela cadeia de responders.
     *
     * Fora da fase, mantém eventos nativos/GUI para título e menus.
     */
    void ProcessAction(
        const std::string& action,
        bool pressed
    )
    {
        LogInput(
            "action " +
            action +
            " " +
            (pressed ? "1" : "0")
        );

        if (!IsGameplayInputContext())
        {
            ProcessMenuOrTitleAction(
                action,
                pressed
            );
            return;
        }

        if (action == "forward")
        {
            SetGameplayButton(
                Button_Forward,
                pressed
            );
        }
        else if (action == "back")
        {
            SetGameplayButton(
                Button_Back,
                pressed
            );
        }
        else if (action == "left")
        {
            SetGameplayButton(
                Button_Left,
                pressed
            );
        }
        else if (action == "right")
        {
            SetGameplayButton(
                Button_Right,
                pressed
            );
        }
        else if (action == "attack")
        {
            SetGameplayButton(
                Button_Attack,
                pressed
            );
        }
        else if (action == "use")
        {
            SetGameplayButton(
                Button_Use,
                pressed
            );
        }
        else if (action == "speed")
        {
            SetGameplayButton(
                Button_Speed,
                pressed
            );
        }
    }

    void ProcessPulseCommand(
        const std::string& command
    )
    {
        LogInput("pulse " + command);

        if (command == "escape")
        {
            ReleaseAllGameplayButtons();

            if (
                menuactive != MENU_Off ||
                GUICapture
            )
            {
                PostGuiPulse(GK_ESCAPE);
            }
            else
            {
                PostNativePulse(
                    KEY_ESCAPE,
                    GK_ESCAPE
                );
            }

            return;
        }

        if (command == "weapprev")
        {
            if (
                menuactive != MENU_Off ||
                GUICapture
            )
            {
                PostGuiPulse(GK_PGUP);
            }
            else
            {
                QueueConsoleCommand(
                    "weapprev"
                );
            }
        }
        else if (command == "weapnext")
        {
            if (
                menuactive != MENU_Off ||
                GUICapture
            )
            {
                PostGuiPulse(GK_PGDN);
            }
            else
            {
                QueueConsoleCommand(
                    "weapnext"
                );
            }
        }
    }

    void ProcessCommand(const std::string& raw)
    {
        const std::string commandLine = Trim(raw);

        if (commandLine.empty())
        {
            return;
        }

        LogInput("received " + commandLine);

        std::istringstream input(commandLine);
        std::string operation;
        input >> operation;

        if (operation == "SAVE")
        {
            std::string slot;
            input >> slot;

            if (SafeToken(slot))
            {
                QueueConsoleCommand(
                    "save " +
                    slot +
                    " \"DoomCraft Auto Save\""
                );
            }
        }
        else if (operation == "LOAD")
        {
            std::string slot;
            input >> slot;

            if (SafeToken(slot))
            {
                QueueConsoleCommand("load " + slot);
            }
        }
        else if (operation == "PAUSE")
        {
            ReleaseAllGameplayButtons();
            SetPaused(true);
        }
        else if (operation == "RESUME")
        {
            SetPaused(false);
        }
        else if (operation == "QUIT")
        {
            ReleaseAllGameplayButtons();
            QueueConsoleCommand("quit");
        }
        else if (operation == "ACTION")
        {
            std::string action;
            int pressedValue = 0;

            input >> action >> pressedValue;

            ProcessAction(
                action,
                pressedValue != 0
            );
        }
        else if (operation == "COMMAND")
        {
            std::string command;
            input >> command;
            ProcessPulseCommand(command);
        }
        else
        {
            LogInput("ignored operation " + operation);
        }
    }

    void ProcessCommands()
    {
        if (commandDirectory.empty())
        {
            return;
        }

        std::vector<fs::path> files;
        std::error_code error;

        for (
            const auto& entry :
            fs::directory_iterator(
                commandDirectory,
                error
            )
        )
        {
            if (
                entry.is_regular_file() &&
                entry.path().extension() == ".cmd"
            )
            {
                files.push_back(entry.path());
            }
        }

        std::sort(files.begin(), files.end());

        if (files.size() > 64)
        {
            files.resize(64);
        }

        for (const fs::path& path : files)
        {
            std::ifstream stream(
                path,
                std::ios::binary
            );

            std::string line;
            std::getline(stream, line);

            if (stream || !line.empty())
            {
                ProcessCommand(line);
            }

            fs::remove(path, error);
        }
    }

    void AppendU32(
        std::ofstream& output,
        uint32_t value
    )
    {
        const unsigned char bytes[4] = {
            static_cast<unsigned char>(
                value & 0xff
            ),
            static_cast<unsigned char>(
                (value >> 8) & 0xff
            ),
            static_cast<unsigned char>(
                (value >> 16) & 0xff
            ),
            static_cast<unsigned char>(
                (value >> 24) & 0xff
            )
        };

        output.write(
            reinterpret_cast<const char*>(bytes),
            4
        );
    }

    void AppendU64(
        std::ofstream& output,
        uint64_t value
    )
    {
        AppendU32(
            output,
            static_cast<uint32_t>(
                value & 0xffffffffULL
            )
        );

        AppendU32(
            output,
            static_cast<uint32_t>(
                value >> 32
            )
        );
    }

    bool CaptureRgba(
        DFrameBuffer* framebuffer,
        std::vector<uint8_t>& output
    )
    {
        if (framebuffer == nullptr)
        {
            return false;
        }

        int pitch = 0;
        ESSType colorType = SS_RGB;
        float gamma = 1.0f;

        auto screenshot =
            framebuffer->GetScreenshotBuffer(
                pitch,
                colorType,
                gamma
            );

        if (
            screenshot.Size() == 0 ||
            pitch == 0
        )
        {
            return false;
        }

        const int sourceWidth =
            framebuffer->GetWidth();
        const int sourceHeight =
            framebuffer->GetHeight();

        if (
            sourceWidth <= 0 ||
            sourceHeight <= 0
        )
        {
            return false;
        }

        const int sourceBytesPerPixel =
            colorType == SS_PAL
                ? 1
                : (
                    colorType == SS_RGB
                        ? 3
                        : 4
                );

        const uint8_t* firstRow =
            screenshot.Data();

        if (pitch < 0)
        {
            firstRow +=
                static_cast<ptrdiff_t>(
                    sourceHeight - 1
                ) *
                static_cast<ptrdiff_t>(
                    -pitch
                );
        }

        output.resize(
            static_cast<size_t>(
                OUTPUT_WIDTH
            ) *
            OUTPUT_HEIGHT *
            4
        );

        for (
            int y = 0;
            y < OUTPUT_HEIGHT;
            ++y
        )
        {
            const int sourceY = std::clamp(
                y * sourceHeight /
                    OUTPUT_HEIGHT,
                0,
                sourceHeight - 1
            );

            const uint8_t* row =
                firstRow +
                static_cast<ptrdiff_t>(
                    sourceY
                ) *
                pitch;

            for (
                int x = 0;
                x < OUTPUT_WIDTH;
                ++x
            )
            {
                const int sourceX =
                    std::clamp(
                        x * sourceWidth /
                            OUTPUT_WIDTH,
                        0,
                        sourceWidth - 1
                    );

                const uint8_t* pixel =
                    row +
                    static_cast<ptrdiff_t>(
                        sourceX
                    ) *
                    sourceBytesPerPixel;

                const size_t destination =
                    (
                        static_cast<size_t>(y) *
                        OUTPUT_WIDTH +
                        x
                    ) *
                    4;

                if (colorType == SS_PAL)
                {
                    const auto color =
                        GPalette.BaseColors[
                            *pixel
                        ];

                    output[destination + 0] =
                        color.r;
                    output[destination + 1] =
                        color.g;
                    output[destination + 2] =
                        color.b;
                }
                else if (colorType == SS_RGB)
                {
                    output[destination + 0] =
                        pixel[0];
                    output[destination + 1] =
                        pixel[1];
                    output[destination + 2] =
                        pixel[2];
                }
                else
                {
                    output[destination + 0] =
                        pixel[2];
                    output[destination + 1] =
                        pixel[1];
                    output[destination + 2] =
                        pixel[0];
                }

                output[destination + 3] = 255;
            }
        }

        return true;
    }

    void WriteFrame(
        const std::vector<uint8_t>& rgba
    )
    {
        const fs::path temporary =
            bridgeDirectory /
            (
                "frame." +
                std::to_string(frameSequence) +
                ".tmp"
            );

        std::ofstream output(
            temporary,
            std::ios::binary |
            std::ios::trunc
        );

        if (!output)
        {
            return;
        }

        AppendU32(output, FRAME_MAGIC);
        AppendU32(output, FRAME_VERSION);
        AppendU32(output, OUTPUT_WIDTH);
        AppendU32(output, OUTPUT_HEIGHT);
        AppendU64(output, frameSequence);
        AppendU32(
            output,
            FRAME_FORMAT_RGBA8
        );
        AppendU32(
            output,
            static_cast<uint32_t>(
                rgba.size()
            )
        );

        output.write(
            reinterpret_cast<const char*>(
                rgba.data()
            ),
            static_cast<std::streamsize>(
                rgba.size()
            )
        );

        output.close();

        if (!output)
        {
            return;
        }

        std::error_code error;

#ifdef _WIN32
        fs::remove(framePath, error);
        error.clear();
#endif

        fs::rename(
            temporary,
            framePath,
            error
        );

        if (error)
        {
            fs::remove(temporary, error);
        }
    }
}

namespace DoomCraftBridge
{
    void OnFrame(DFrameBuffer* framebuffer)
    {
        Initialize();

        if (bridgeDirectory.empty())
        {
            return;
        }

        /*
         * Sempre processa comandos, mesmo se o bridge estiver pausado.
         */
        ProcessCommands();

        if (bridgePaused)
        {
            return;
        }

        const auto now =
            std::chrono::steady_clock::now();

        if (
            lastFrameTime
                    .time_since_epoch()
                    .count() != 0 &&
            now - lastFrameTime <
                FRAME_INTERVAL
        )
        {
            return;
        }

        lastFrameTime = now;

        std::vector<uint8_t> rgba;

        if (CaptureRgba(framebuffer, rgba))
        {
            ++frameSequence;
            WriteFrame(rgba);
        }
    }
}
