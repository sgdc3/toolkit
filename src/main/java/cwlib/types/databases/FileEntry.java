package cwlib.types.databases;

import cwlib.enums.ResourceKeys;
import cwlib.types.data.ResourceInfo;
import cwlib.types.data.SHA1;
import cwlib.types.swing.FileData;
import cwlib.types.swing.FileNode;
import toolkit.utilities.ResourceSystem;

public abstract class FileEntry {
    /**
     * The database this entry comes from.
     */
    protected final FileData source;

    /**
     * The node that contains this entry
     */
    private final FileNode node;
    
    /**
     * Path of resource in a database.
     */
    protected String path;
    
    /**
     * Size of the associated data buffer.
     */
    protected long size;
    
    /**
     * SHA1 signature of data.
     */
    protected SHA1 sha1;

    /**
     * Unique key for data
     */
    protected Object key;

    /**
     * Resource metadata
     */
    private ResourceInfo info;

    /**
     * Map of resources assigned to this entry.
     */
    private Object[] resources = new Object[ResourceKeys.MAX_ENTRIES];

    /**
     * Creates a FileEntry using default parameters for FileDB.
     * If one wants to create an entry in a database, they should refer to FileDB.newFileEntry instead.
     * @param path Path of entry in database
     * @param source Database that owns this FileEntry
     * @param size Size of entry's data
     * @param sha1 SHA1 signature of entry's data
     */
    public FileEntry(FileData source, String path, SHA1 sha1, long size) {
        if (path == null)
            throw new NullPointerException("Path provided to FileEntry constructor cannot be null!");
        if (source == null)
            throw new NullPointerException("File database provided to FileEntry constructor cannot be null!");
        if (sha1 == null)
            throw new NullPointerException("File SHA1 provided to FileEntry constructor cannot be null!");

        this.source = source;
        this.path = path;
        this.size = size;
        this.sha1 = sha1;

        this.node = ResourceSystem.API_MODE ? null : source.addNode(this);
    }
    
    public FileData getSource() { return this.source; }
    public FileNode getNode() { return this.node; }
    public String getPath() { return this.path; }
    public SHA1 getSHA1() { return this.sha1; }
    public long getSize() { return this.size; }
    public Object getKey() { return this.key; }
    public ResourceInfo getInfo() { return this.info; }

    public void setPath(String path) {
        // Null strings are annoying, plus it works functionally the same anyway.
        if (path == null) path = "";
        if (path.equals(this.path)) return;

        this.path = path;
        this.source.setHasChanges();
    }

    public void setSHA1(SHA1 sha1) {
        if (sha1 == null) sha1 = new SHA1();
        if (this.sha1.equals(sha1)) return;
        this.sha1 = sha1; 
        this.source.setHasChanges();
    }

    public void setSize(long size) { 
        if (size == this.size) return;
        this.size = size;
        this.source.setHasChanges();
    }

    public void setInfo(ResourceInfo info) { this.info = info; }

    /**
     * Sets entry data from buffer (hash, size, cache).
     * @param buffer Buffer to use as base
     */
    public void setDetails(byte[] buffer) {
        if (buffer == null) {
            this.setSHA1(null);
            this.setSize(0);
            return;
        }
        this.setSHA1(SHA1.fromBuffer(buffer));
        this.setSize(buffer.length);
    }

    /**
     * Sets entry data from another entry.
     * @param entry Entry to use as base
     */
    public void setDetails(FileEntry entry) {
        if (entry == null) 
            throw new NullPointerException("Entry cannot be null!");
        this.setSize(entry.getSize());
        this.setSHA1(entry.getSHA1());
    }

    public void setResource(int key, Object resource) {
        this.resources[key] = resource;
    }

    public void remove() { this.source.remove(this); }

    @SuppressWarnings("unchecked")
    public <T> T getResource(int key) {
        Object value = this.resources[key];
        if (value == null) return null;
        return (T) value;
    }

    @Override public String toString() {
        return String.format("FileEntry (%s, %s)", this.path, this.sha1);
    }
}
