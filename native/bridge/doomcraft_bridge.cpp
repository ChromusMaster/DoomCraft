#include "doomcraft_bridge.h"

#include "common/console/c_dispatch.h"
#include "common/engine/d_eventbase.h"
#include "common/engine/i_interface.h"
#include "common/rendering/v_video.h"
#include "common/textures/m_png.h"
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
 * Global mantida pela camada de entrada do LZDoom.
 *
 * true  = menus, telas iniciais e diálogos esperam EV_GUI_Event.
 * false = a fase aceita os comandos normais de gameplay.
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
    fs::path bridgeDirectory;
    fs::path commandDirectory;
    fs::path framePath;
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

    void Initialize()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        const char* environment = std::getenv("DOOMCRAFT_BRIDGE_DIR");

        if (environment == nullptr || *environment == '\0')
        {
            return;
        }

        bridgeDirectory = fs::u8path(environment);
        commandDirectory = bridgeDirectory / "commands";
        framePath = bridgeDirectory / "frame.bin";

        std::error_code error;
        fs::create_directories(commandDirectory, error);
    }

    void QueueConsoleCommand(const std::string& command)
    {
        AddCommandString(command.c_str());
    }

    void PostGuiKey(int guiKey, bool pressed)
    {
        event_t event{};
        event.type = EV_GUI_Event;
        event.subtype = pressed ? EV_GUI_KeyDown : EV_GUI_KeyUp;
        event.data1 = static_cast<int16_t>(guiKey);
        event.data2 = static_cast<int16_t>(guiKey);
        event.data3 = 0;
        D_PostEvent(&event);
    }

    void PostGuiPulse(int guiKey)
    {
        PostGuiKey(guiKey, true);
        PostGuiKey(guiKey, false);
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

    /*
     * Durante a GUI, setas e Enter precisam virar EV_GUI_Event.
     * Durante a fase, os comandos +forward, +use etc. continuam sendo usados.
     */
    void ProcessAction(const std::string& action, bool pressed)
    {
        if (GUICapture)
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
            else if (action == "use" || action == "attack")
            {
                /*
                 * Enter e Ctrl confirmam menus e liberam telas "press any key".
                 */
                PostGuiKey(GK_RETURN, pressed);
            }

            return;
        }

        static const std::set<std::string> allowedGameplayActions = {
            "forward",
            "back",
            "left",
            "right",
            "attack",
            "use",
            "speed"
        };

        if (allowedGameplayActions.find(action) ==
            allowedGameplayActions.end())
        {
            return;
        }

        QueueConsoleCommand(
            std::string(pressed ? "+" : "-") + action
        );
    }

    void ProcessPulseCommand(const std::string& command)
    {
        if (command == "weapprev")
        {
            if (GUICapture)
            {
                PostGuiPulse(GK_PGUP);
            }
            else
            {
                QueueConsoleCommand(command);
            }
        }
        else if (command == "weapnext")
        {
            if (GUICapture)
            {
                PostGuiPulse(GK_PGDN);
            }
            else
            {
                QueueConsoleCommand(command);
            }
        }
    }

    void ProcessCommand(const std::string& raw)
    {
        std::istringstream input(Trim(raw));
        std::string operation;
        input >> operation;

        if (operation == "SAVE")
        {
            std::string slot;
            input >> slot;

            if (SafeToken(slot))
            {
                QueueConsoleCommand(
                    "save " + slot + " \"DoomCraft Auto Save\""
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
            SetPaused(true);
        }
        else if (operation == "RESUME")
        {
            SetPaused(false);
        }
        else if (operation == "QUIT")
        {
            QueueConsoleCommand("quit");
        }
        else if (operation == "ACTION")
        {
            std::string action;
            int pressedValue = 0;
            input >> action >> pressedValue;
            ProcessAction(action, pressedValue != 0);
        }
        else if (operation == "COMMAND")
        {
            std::string command;
            input >> command;
            ProcessPulseCommand(command);
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

        for (const auto& entry :
             fs::directory_iterator(commandDirectory, error))
        {
            if (entry.is_regular_file() &&
                entry.path().extension() == ".cmd")
            {
                files.push_back(entry.path());
            }
        }

        std::sort(files.begin(), files.end());

        if (files.size() > 32)
        {
            files.resize(32);
        }

        for (const fs::path& path : files)
        {
            std::ifstream stream(path, std::ios::binary);
            std::string line;
            std::getline(stream, line);

            if (stream || !line.empty())
            {
                ProcessCommand(line);
            }

            fs::remove(path, error);
        }
    }

    void AppendU32(std::ofstream& output, uint32_t value)
    {
        const unsigned char bytes[4] = {
            static_cast<unsigned char>(value & 0xff),
            static_cast<unsigned char>((value >> 8) & 0xff),
            static_cast<unsigned char>((value >> 16) & 0xff),
            static_cast<unsigned char>((value >> 24) & 0xff)
        };

        output.write(
            reinterpret_cast<const char*>(bytes),
            4
        );
    }

    void AppendU64(std::ofstream& output, uint64_t value)
    {
        AppendU32(
            output,
            static_cast<uint32_t>(value & 0xffffffffULL)
        );

        AppendU32(
            output,
            static_cast<uint32_t>(value >> 32)
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

        auto screenshot = framebuffer->GetScreenshotBuffer(
            pitch,
            colorType,
            gamma
        );

        if (screenshot.Size() == 0 || pitch == 0)
        {
            return false;
        }

        const int sourceWidth = framebuffer->GetWidth();
        const int sourceHeight = framebuffer->GetHeight();

        if (sourceWidth <= 0 || sourceHeight <= 0)
        {
            return false;
        }

        const int sourceBytesPerPixel =
            colorType == SS_PAL
                ? 1
                : (colorType == SS_RGB ? 3 : 4);

        const uint8_t* firstRow = screenshot.Data();

        if (pitch < 0)
        {
            firstRow +=
                static_cast<ptrdiff_t>(sourceHeight - 1) *
                static_cast<ptrdiff_t>(-pitch);
        }

        output.resize(
            static_cast<size_t>(OUTPUT_WIDTH) *
            OUTPUT_HEIGHT *
            4
        );

        for (int y = 0; y < OUTPUT_HEIGHT; ++y)
        {
            const int sourceY = std::clamp(
                y * sourceHeight / OUTPUT_HEIGHT,
                0,
                sourceHeight - 1
            );

            const uint8_t* row =
                firstRow +
                static_cast<ptrdiff_t>(sourceY) *
                pitch;

            for (int x = 0; x < OUTPUT_WIDTH; ++x)
            {
                const int sourceX = std::clamp(
                    x * sourceWidth / OUTPUT_WIDTH,
                    0,
                    sourceWidth - 1
                );

                const uint8_t* pixel =
                    row +
                    static_cast<ptrdiff_t>(sourceX) *
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
                        GPalette.BaseColors[*pixel];

                    output[destination + 0] = color.r;
                    output[destination + 1] = color.g;
                    output[destination + 2] = color.b;
                }
                else if (colorType == SS_RGB)
                {
                    output[destination + 0] = pixel[0];
                    output[destination + 1] = pixel[1];
                    output[destination + 2] = pixel[2];
                }
                else
                {
                    output[destination + 0] = pixel[2];
                    output[destination + 1] = pixel[1];
                    output[destination + 2] = pixel[0];
                }

                output[destination + 3] = 255;
            }
        }

        return true;
    }

    void WriteFrame(const std::vector<uint8_t>& rgba)
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
        AppendU32(output, FRAME_FORMAT_RGBA8);
        AppendU32(
            output,
            static_cast<uint32_t>(rgba.size())
        );

        output.write(
            reinterpret_cast<const char*>(rgba.data()),
            static_cast<std::streamsize>(rgba.size())
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

        fs::rename(temporary, framePath, error);

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

        ProcessCommands();

        if (bridgePaused)
        {
            return;
        }

        const auto now = std::chrono::steady_clock::now();

        if (
            lastFrameTime.time_since_epoch().count() != 0 &&
            now - lastFrameTime < FRAME_INTERVAL
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
