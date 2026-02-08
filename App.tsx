import React, {useState, useEffect, useCallback} from 'react';
import {
  StyleSheet,
  View,
  Text,
  Modal,
  TouchableOpacity,
  ToastAndroid,
} from 'react-native';
import ShareMenu from 'react-native-share-menu';
import MarkdownIt from 'markdown-it';
import RNHTMLtoPDF from 'react-native-html-to-pdf';
import RNPrint from 'react-native-print';

const md = new MarkdownIt();

const PAGE_SIZES = ['A4', 'Letter', 'Legal'];
const MARGINS = ['Standard', 'Minimal', 'None'];

const App = () => {
  const [sharedText, setSharedText] = useState<string | null>(null);
  const [pageSize, setPageSize] = useState('A4');
  const [margin, setMargin] = useState('Standard');
  const [fontSize, setFontSize] = useState(12);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleShare = useCallback((item: any) => {
    if (!item) {
      return;
    }
    const {type, value} = item;
    if (type === 'text/plain') {
      setSharedText(value);
    }
  }, []);

  useEffect(() => {
    ShareMenu.getInitialShare(handleShare);
    const listener = ShareMenu.addNewShareListener(handleShare);
    return () => {
      listener.remove();
    };
  }, [handleShare]);

  const getHtml = (text: string, pSize: string, mType: string, fSize: number) => {
    const content = md.render(text);

    const marginMap: {[key: string]: string} = {
      Standard: '20mm',
      Minimal: '10mm',
      None: '0mm',
    };

    const sizeMap: {[key: string]: string} = {
      A4: 'A4',
      Letter: 'letter',
      Legal: 'legal',
    };

    return `
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="utf-8">
          <style>
            @page {
              size: ${sizeMap[pSize] || 'A4'};
              margin: ${marginMap[mType] || '20mm'};
            }
            body {
              font-size: ${fSize}pt;
              font-family: sans-serif;
              line-height: 1.5;
              color: #333;
            }
            img { max-width: 100%; height: auto; }
            pre {
              background: #f4f4f4;
              padding: 10px;
              border-radius: 5px;
              overflow-x: auto;
              white-space: pre-wrap;
              word-wrap: break-word;
            }
            code {
              font-family: monospace;
              background: #f4f4f4;
              padding: 2px 4px;
              border-radius: 3px;
            }
            blockquote {
              border-left: 4px solid #ddd;
              padding-left: 15px;
              color: #777;
              font-style: italic;
            }
            table {
              border-collapse: collapse;
              width: 100%;
              margin-bottom: 20px;
            }
            th, td {
              border: 1px solid #ddd;
              padding: 8px;
              text-align: left;
            }
            th {
              background-color: #f2f2f2;
            }
          </style>
        </head>
        <body>
          ${content}
        </body>
      </html>
    `;
  };

  const handleSave = async () => {
    if (!sharedText || isProcessing) {
      return;
    }
    setIsProcessing(true);
    try {
      const html = getHtml(sharedText, pageSize, margin, fontSize);
      const options = {
        html,
        fileName: `MD_Export_${Date.now()}`,
        directory: 'Documents',
      };

      const file = await RNHTMLtoPDF.convert(options);
      ToastAndroid.show(`PDF saved to: ${file.filePath}`, ToastAndroid.LONG);
      setSharedText(null);
    } catch (error) {
      console.error(error);
      ToastAndroid.show('Failed to save PDF', ToastAndroid.SHORT);
    } finally {
      setIsProcessing(false);
    }
  };

  const handlePrint = async () => {
    if (!sharedText || isProcessing) {
      return;
    }
    setIsProcessing(true);
    try {
      const html = getHtml(sharedText, pageSize, margin, fontSize);

      // First, save the PDF
      const options = {
        html,
        fileName: `MD_Export_${Date.now()}`,
        directory: 'Documents',
      };
      const file = await RNHTMLtoPDF.convert(options);

      // Then, open print dialog
      await RNPrint.print({html});

      ToastAndroid.show(`PDF saved to: ${file.filePath}`, ToastAndroid.LONG);
      setSharedText(null);
    } catch (error) {
      console.error(error);
      ToastAndroid.show('Failed to process Print + Save', ToastAndroid.SHORT);
    } finally {
      setIsProcessing(false);
    }
  };

  if (!sharedText) {
    return (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyText}>MdToPdf</Text>
        <Text style={styles.subText}>
          Share Markdown text from any app to convert it to PDF.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Modal transparent visible={!!sharedText} animationType="fade">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.title}>PDF Settings</Text>

            <Text style={styles.label}>Page Size</Text>
            <View style={styles.row}>
              {PAGE_SIZES.map(size => (
                <TouchableOpacity
                  key={size}
                  disabled={isProcessing}
                  style={[styles.chip, pageSize === size && styles.chipActive]}
                  onPress={() => setPageSize(size)}>
                  <Text
                    style={
                      pageSize === size ? styles.chipTextActive : styles.chipText
                    }>
                    {size}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={styles.label}>Margins</Text>
            <View style={styles.row}>
              {MARGINS.map(m => (
                <TouchableOpacity
                  key={m}
                  disabled={isProcessing}
                  style={[styles.chip, margin === m && styles.chipActive]}
                  onPress={() => setMargin(m)}>
                  <Text
                    style={
                      margin === m ? styles.chipTextActive : styles.chipText
                    }>
                    {m}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={styles.label}>Font Size: {fontSize}pt</Text>
            <View style={styles.row}>
              <TouchableOpacity
                disabled={isProcessing}
                style={styles.buttonSmall}
                onPress={() => setFontSize(f => Math.max(8, f - 1))}>
                <Text style={styles.controlText}>-</Text>
              </TouchableOpacity>
              <TouchableOpacity
                disabled={isProcessing}
                style={styles.buttonSmall}
                onPress={() => setFontSize(f => Math.min(30, f + 1))}>
                <Text style={styles.controlText}>+</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.footer}>
              <TouchableOpacity
                disabled={isProcessing}
                style={[styles.button, isProcessing && styles.buttonDisabled]}
                onPress={handleSave}>
                <Text style={styles.buttonText}>
                  {isProcessing ? '...' : 'Save'}
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                disabled={isProcessing}
                style={[
                  styles.button,
                  styles.buttonPrint,
                  isProcessing && styles.buttonDisabled,
                ]}
                onPress={handlePrint}>
                <Text style={styles.buttonText}>
                  {isProcessing ? '...' : 'Print + Save'}
                </Text>
              </TouchableOpacity>
            </View>

            <TouchableOpacity
              disabled={isProcessing}
              style={styles.closeButton}
              onPress={() => setSharedText(null)}>
              <Text style={styles.closeButtonText}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f9fa',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 30,
  },
  emptyText: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 10,
  },
  subText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    width: '85%',
    backgroundColor: 'white',
    padding: 24,
    borderRadius: 20,
    elevation: 20,
  },
  title: {
    fontSize: 22,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
    color: '#1a1a1a',
  },
  label: {
    fontSize: 14,
    marginTop: 15,
    marginBottom: 10,
    color: '#555',
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 5,
  },
  chip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: '#eee',
    marginRight: 10,
    marginBottom: 10,
    backgroundColor: '#fafafa',
  },
  chipActive: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  chipText: {
    color: '#555',
    fontWeight: '500',
  },
  chipTextActive: {
    color: 'white',
    fontWeight: '600',
  },
  buttonSmall: {
    width: 48,
    height: 48,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: '#eee',
    borderRadius: 24,
    marginRight: 15,
    backgroundColor: '#fafafa',
  },
  controlText: {
    fontSize: 20,
    color: '#333',
    fontWeight: 'bold',
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 35,
  },
  button: {
    flex: 1,
    backgroundColor: '#34C759',
    paddingVertical: 15,
    borderRadius: 12,
    alignItems: 'center',
    marginRight: 8,
    elevation: 2,
  },
  buttonPrint: {
    backgroundColor: '#007AFF',
    marginLeft: 8,
  },
  buttonDisabled: {
    backgroundColor: '#ccc',
    elevation: 0,
  },
  buttonText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 16,
  },
  closeButton: {
    marginTop: 25,
    alignSelf: 'center',
    padding: 10,
  },
  closeButtonText: {
    color: '#FF3B30',
    fontWeight: '700',
    fontSize: 15,
  },
});

export default App;
