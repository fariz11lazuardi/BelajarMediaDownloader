using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

internal static class FolderPicker
{
    [ComImport, Guid("42f85136-db7e-439c-85f1-e4075d135fc8"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IFileDialog
    {
        [PreserveSig] int Show(IntPtr owner);
        // Unused entries retain their positions in the IFileDialog vtable.
        void SetFileTypes();
        void SetFileTypeIndex();
        void GetFileTypeIndex();
        void Advise();
        void Unadvise();
        void SetOptions(uint options);
        void GetOptions(out uint options);
        void SetDefaultFolder();
        void SetFolder(IShellItem folder);
        void GetFolder();
        void GetCurrentSelection();
        void SetFileName();
        void GetFileName();
        void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string title);
        void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
        void SetFileNameLabel();
        void GetResult(out IShellItem item);
    }

    [ComImport, Guid("43826d1e-e718-42ee-bc55-a1e261c37bfe"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IShellItem
    {
        void BindToHandler();
        void GetParent();
        void GetDisplayName(uint kind, out IntPtr name);
    }

    private delegate bool EnumWindowsCallback(IntPtr handle, IntPtr data);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsCallback callback, IntPtr data);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr handle);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr handle, out uint processId);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
    private static extern void SHCreateItemFromParsingName(
        string path, IntPtr context, ref Guid id, out IShellItem item);

    private static IntPtr FindOwner(uint processId)
    {
        IntPtr owner = IntPtr.Zero;
        EnumWindows(delegate(IntPtr handle, IntPtr data)
        {
            uint id;
            GetWindowThreadProcessId(handle, out id);
            if (id != processId || !IsWindowVisible(handle)) return true;
            owner = handle;
            return false;
        }, IntPtr.Zero);
        return owner;
    }

    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Length != 3) return 2;
        IFileDialog dialog = null;
        try
        {
            var dialogClass = new Guid("dc1c5a9c-e88a-4dde-a5a1-60f82a20aef7");
            dialog = (IFileDialog)Activator.CreateInstance(Type.GetTypeFromCLSID(dialogClass));
            uint options;
            dialog.GetOptions(out options);
            const uint PickFolders = 0x20, ForceFileSystem = 0x40, PathMustExist = 0x800;
            dialog.SetOptions(options | PickFolders | ForceFileSystem | PathMustExist);
            dialog.SetTitle("Pilih folder tujuan unduhan");
            dialog.SetOkButtonLabel("Pilih folder");

            if (Directory.Exists(args[2]))
            {
                IShellItem startFolder;
                var id = typeof(IShellItem).GUID;
                SHCreateItemFromParsingName(args[2], IntPtr.Zero, ref id, out startFolder);
                try { dialog.SetFolder(startFolder); }
                finally { Marshal.ReleaseComObject(startFolder); }
            }

            int result = dialog.Show(FindOwner(uint.Parse(args[1])));
            if (result == unchecked((int)0x800704C7)) return 1;
            Marshal.ThrowExceptionForHR(result);

            IShellItem selected;
            dialog.GetResult(out selected);
            try
            {
                IntPtr name;
                selected.GetDisplayName(0x80058000, out name); // SIGDN_FILESYSPATH
                try { File.WriteAllText(args[0], Marshal.PtrToStringUni(name), new UTF8Encoding(false)); }
                finally { Marshal.FreeCoTaskMem(name); }
            }
            finally { Marshal.ReleaseComObject(selected); }
            return 0;
        }
        catch (Exception) { return 2; }
        finally { if (dialog != null) Marshal.ReleaseComObject(dialog); }
    }
}
