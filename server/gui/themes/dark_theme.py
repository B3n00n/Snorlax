import dearpygui.dearpygui as dpg

def apply_dark_theme():
    with dpg.theme() as global_theme:
        with dpg.theme_component(dpg.mvAll):
            # Window background
            dpg.add_theme_color(dpg.mvThemeCol_WindowBg, (30, 30, 30))
            dpg.add_theme_color(dpg.mvThemeCol_ChildBg, (25, 25, 25))
            dpg.add_theme_color(dpg.mvThemeCol_PopupBg, (35, 35, 35))
            
            # Borders
            dpg.add_theme_color(dpg.mvThemeCol_Border, (70, 70, 70))
            dpg.add_theme_color(dpg.mvThemeCol_BorderShadow, (0, 0, 0))
            
            # Frame backgrounds
            dpg.add_theme_color(dpg.mvThemeCol_FrameBg, (45, 45, 45))
            dpg.add_theme_color(dpg.mvThemeCol_FrameBgHovered, (55, 55, 55))
            dpg.add_theme_color(dpg.mvThemeCol_FrameBgActive, (65, 65, 65))
            
            # Title bar
            dpg.add_theme_color(dpg.mvThemeCol_TitleBg, (25, 25, 25))
            dpg.add_theme_color(dpg.mvThemeCol_TitleBgActive, (35, 35, 35))
            dpg.add_theme_color(dpg.mvThemeCol_TitleBgCollapsed, (20, 20, 20))
            
            # Scrollbar
            dpg.add_theme_color(dpg.mvThemeCol_ScrollbarBg, (20, 20, 20))
            dpg.add_theme_color(dpg.mvThemeCol_ScrollbarGrab, (55, 55, 55))
            dpg.add_theme_color(dpg.mvThemeCol_ScrollbarGrabHovered, (70, 70, 70))
            dpg.add_theme_color(dpg.mvThemeCol_ScrollbarGrabActive, (85, 85, 85))
            
            # Buttons
            dpg.add_theme_color(dpg.mvThemeCol_Button, (55, 55, 55))
            dpg.add_theme_color(dpg.mvThemeCol_ButtonHovered, (70, 70, 70))
            dpg.add_theme_color(dpg.mvThemeCol_ButtonActive, (85, 85, 85))
            
            # Headers
            dpg.add_theme_color(dpg.mvThemeCol_Header, (50, 50, 50))
            dpg.add_theme_color(dpg.mvThemeCol_HeaderHovered, (65, 65, 65))
            dpg.add_theme_color(dpg.mvThemeCol_HeaderActive, (80, 80, 80))
            
            # Text
            dpg.add_theme_color(dpg.mvThemeCol_Text, (200, 200, 200))
            dpg.add_theme_color(dpg.mvThemeCol_TextDisabled, (128, 128, 128))
            
            # Plot
            dpg.add_theme_color(dpg.mvThemeCol_PlotLines, (100, 200, 100))
            dpg.add_theme_color(dpg.mvThemeCol_PlotHistogram, (100, 150, 250))
            
            # Style
            dpg.add_theme_style(dpg.mvStyleVar_WindowRounding, 5)
            dpg.add_theme_style(dpg.mvStyleVar_FrameRounding, 3)
            dpg.add_theme_style(dpg.mvStyleVar_ScrollbarRounding, 3)
            dpg.add_theme_style(dpg.mvStyleVar_WindowPadding, 8, 8)
            dpg.add_theme_style(dpg.mvStyleVar_FramePadding, 4, 3)
            dpg.add_theme_style(dpg.mvStyleVar_ItemSpacing, 8, 4)
    
    dpg.bind_theme(global_theme)


def get_status_colors():
    """Get colors for different status indicators"""
    return {
        'connected': (100, 200, 100),
        'disconnected': (200, 100, 100),
        'charging': (100, 200, 250),
        'battery_low': (250, 150, 100),
        'battery_critical': (250, 100, 100),
        'success': (100, 250, 100),
        'error': (250, 100, 100),
        'warning': (250, 200, 100),
        'info': (100, 150, 250)
    }