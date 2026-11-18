using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

internal static class Scalo {
    [STAThread]
    private static void Main() {
        try {
            string profile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Scalo");
            Directory.CreateDirectory(profile);
            string html = Path.Combine(profile, "Scalo-Studio.html");
            string temp = Path.Combine(profile, Guid.NewGuid().ToString("N") + ".tmp");
            using (var src = Assembly.GetExecutingAssembly().GetManifestResourceStream("studio.html"))
            using (var dest = File.Create(temp)) src.CopyTo(dest);
            if (File.Exists(html)) File.Replace(temp, html, null);
            else File.Move(temp, html);
            foreach (var root in new[] {Environment.SpecialFolder.ProgramFilesX86, Environment.SpecialFolder.ProgramFiles, Environment.SpecialFolder.LocalApplicationData}) {
                var edge = Path.Combine(Environment.GetFolderPath(root), @"Microsoft\Edge\Application\msedge.exe");
                if (!File.Exists(edge)) continue;
                Process.Start(new ProcessStartInfo(edge, "--app=\"" + new Uri(html).AbsoluteUri + "\" --user-data-dir=\"" + Path.Combine(profile, "Browser") + "\" --no-first-run") {UseShellExecute=false});
                return;
            }
            MessageBox.Show("Install Microsoft Edge to run Scalo on this PC.", "Scalo");
        } catch (Exception e) { MessageBox.Show(e.Message, "Scalo"); }
    }
}
